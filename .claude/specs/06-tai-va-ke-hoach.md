# 06 — Đánh giá tải và kế hoạch triển khai

## Năm con số khác nhau, hay bị gọi chung là "5.000 user"

| Con số | Nghĩa | Ảnh hưởng tới cái gì |
|--------|-------|----------------------|
| **Tổng tài khoản** | Số người có thể đăng nhập | Dung lượng DB, số hàng `conversation_member` |
| **User online** | Số người đang mở app | Số session, heap |
| **Số kết nối WebSocket** | User online × số thiết bị/tab | File descriptor, heap, giới hạn OS |
| **Tin gửi mỗi giây** | Lượng ghi | Transaction/s của Postgres, lượng event Kafka |
| **Lượt phát tới thiết bị mỗi giây** | Tin/s × số người nhận × số session mỗi người | CPU, băng thông — **thường lớn hơn tin/s hàng chục lần** |

Con số cuối hay bị bỏ qua, và nó là con số thực sự giới hạn hệ thống.

## Kịch bản tải giả định

Giả định (đều là **giả định**, chưa đo, chưa được xác nhận):

| Tham số | Giá trị | Từ đâu |
|---------|---------|--------|
| Tổng tài khoản | 5.000 | Đề bài, hiểu theo nghĩa tổng tài khoản |
| Tỉ lệ online lúc cao điểm | 20% | Giả định cho nền tảng đối tác dùng trong giờ làm việc |
| Thiết bị/tab trung bình mỗi người online | 1,6 | Giả định: phần lớn 1, một số mở cả web và điện thoại |
| Tin mỗi người online mỗi phút (cao điểm) | 2 | Giả định cho chat công việc, không phải chat xã hội |
| Tỉ lệ DM / nhóm | 70% / 30% | Giả định |
| Số thành viên trung bình một nhóm | 8 | Giả định (tối đa 50 theo Q5) |

Phép tính:

```
User online             = 5.000 x 20%            = 1.000
Ket noi WebSocket       = 1.000 x 1,6            = 1.600 ket noi
Tin gui moi giay        = 1.000 x 2 / 60         = 33 tin/s
Nguoi nhan trung binh   = 0,7 x 1 + 0,3 x 8      = 3,1 nguoi/tin
Luot phat toi thiet bi  = 33 x 3,1 x 1,6         = 164 frame/s
Event Kafka             = 33 tin/s + ~20 read/s  = ~53 event/s
```

Đọc kết quả:

- **33 transaction ghi/giây:** một Postgres cấu hình bình thường xử lý mức này gần như không thấy. Mỗi transaction gồm 5 câu lệnh ngắn trên index.
- **164 frame WebSocket/giây**, mỗi frame khoảng 400 byte, tức ~65 KB/s. Không đáng kể.
- **1.600 kết nối trên một JVM:** khả thi. Mỗi kết nối tốn khoảng 30–60 KB heap (buffer + state STOMP), nên ~50–100 MB chỉ để giữ kết nối. Phải đặt `-Xmx` đủ và nâng giới hạn file descriptor của OS.
- **53 event/giây qua Kafka:** một partition cũng dư; 6 partition là để mở rộng, không phải vì tải.

### Nếu 5.000 là số online đồng thời

Mọi con số nhân 5: **8.000 kết nối**, ~165 tin/s, ~820 frame/s. Lúc đó:

- 8.000 kết nối trên một JVM là vùng **phải đo thật**, không phải vùng ước lượng. Khả thi về lý thuyết, nhưng heap và GC pause trở thành vấn đề chính.
- 165 transaction/s vẫn ổn với Postgres.
- Quan trọng nhất: **một instance trở thành điểm chết duy nhất** cho 8.000 người. Lúc này phải tính instance thứ hai ngay từ MVP, tức là làm Cách 1 trong [03](03-kafka.md) sớm hơn dự kiến.

Đây là lý do câu hỏi 1 trong [README](README.md) là câu hỏi quan trọng nhất.

### Không khẳng định chịu được 5.000 user

Các con số trên là **phép tính trên giả định**, không phải kết quả đo. Chúng chỉ chứng minh một điều: **không có con số nào ở mức rõ ràng là quá sức**, nên hướng kiến trúc này hợp lý. Chúng **không** chứng minh hệ thống chạy được ở mức đó.

Trước khi nói "chịu được", phải đo ba thứ trên môi trường giống production:

| Phải đo | Cách đo | Ngưỡng cần biết |
|---------|---------|-----------------|
| Số kết nối WebSocket tối đa một instance giữ được | k6 hoặc Gatling mở N kết nối rồi giữ, theo dõi heap và GC | Khi nào heap vượt 70% hoặc GC pause > 200ms |
| Độ trễ từ lúc `POST` tới lúc frame tới thiết bị | Client đo `createdAt` so với lúc nhận | p95 nên dưới 1s; outbox trễ sẽ hiện ra ở đây |
| Độ tồn đọng outbox dưới tải | Đếm số dòng `published_at IS NULL` theo thời gian | Tăng đơn điệu nghĩa là publisher không đủ nhanh |

Mục tiêu của ba phép đo không phải lấy con số đẹp để báo cáo, mà là biết **ngưỡng vỡ** nằm ở đâu để đặt cảnh báo trước ngưỡng đó.

---

## Kế hoạch triển khai

Thứ tự theo phụ thuộc. Mỗi bước phải chạy được và kiểm chứng được **trước khi** sang bước sau.

### Bước 1 — Hạ tầng chạy được tại máy local

- **Kết quả:** `docker-compose.yml` có PostgreSQL + Kafka (một broker, KRaft). `application.yaml` có datasource và Kafka. Flyway `V1__init.sql` tạo 4 bảng cùng index ở [04](04-du-lieu-va-api.md).
- **Kiểm chứng:** `./mvnw spring-boot:run` lên được; Flyway log báo đã áp dụng V1; `ChatServiceApplicationTests.contextLoads` xanh với Testcontainers.
- **Hoàn thành khi:** người khác clone về và chạy được chỉ bằng `docker compose up` + `./mvnw test`, không phải hỏi gì.

### Bước 2 — Hội thoại và thành viên

- **Kết quả:** `POST /conversations` (DM idempotent qua `dm_key`, và nhóm), `GET /conversations`.
- **Kiểm chứng:** gọi `POST` DM hai lần → cùng một `conversationId`; hai luồng song song tạo cùng DM → đúng một hàng trong DB.
- **Hoàn thành khi:** không có cách nào tạo được hai DM cho cùng một cặp user, kể cả khi gọi song song.

### Bước 3 — Gửi tin (chưa có realtime)

- **Kết quả:** `POST /conversations/{id}/messages` với transaction đầy đủ ở [04](04-du-lieu-va-api.md): kiểm membership, cấp `seq`, insert message, cập nhật `last_message_*` và `last_read_seq`, insert `outbox_event`.
- **Kiểm chứng:** gửi 100 tin tuần tự → `seq` là 1..100 không hở; **gửi song song 50 tin từ 5 luồng → `seq` vẫn liên tục, không trùng** (test quan trọng nhất của cả dự án); gửi lại cùng `clientMessageId` → `200` với cùng `messageId`, bảng `message` không thêm dòng.
- **Hoàn thành khi:** test đồng thời chạy xanh 10 lần liên tiếp, và `outbox_event` có đúng một dòng cho mỗi tin.

### Bước 4 — Đọc lịch sử

- **Kết quả:** `GET /conversations/{id}/messages` với `beforeSeq` và `afterSeq`, cắt theo `join_seq`.
- **Kiểm chứng:** thêm thành viên mới sau 50 tin → người đó chỉ thấy tin từ 51; phân trang lùi hết 50 tin không lặp và không thiếu tin nào.
- **Hoàn thành khi:** `EXPLAIN` cho thấy truy vấn dùng index `(conversation_id, seq DESC)`, không có `Seq Scan`.

### Bước 5 — Outbox publisher sang Kafka

- **Kết quả:** job `@Scheduled` quét `published_at IS NULL ORDER BY id`, publish với key = `conversationId`, đánh dấu đã publish; producer `acks=all` + `enable.idempotence=true`.
- **Kiểm chứng:** gửi tin rồi đọc topic bằng `kafka-console-consumer` → thấy event đúng key, đúng thứ tự; **tắt Kafka, gửi 10 tin, bật lại** → cả 10 event vào Kafka đúng thứ tự, không mất.
- **Hoàn thành khi:** phép thử tắt-bật Kafka ở trên đạt. Đây là lý do tồn tại của outbox — không test thì không biết nó có hoạt động.

### Bước 6 — WebSocket và fan-out

- **Kết quả:** endpoint `/ws/chat` STOMP, `HandshakeInterceptor` lấy danh tính; consumer `chat-ws-fanout` đọc thành viên từ DB rồi `convertAndSendToUser`; các giới hạn buffer ở [04](04-du-lieu-va-api.md).
- **Kiểm chứng:** hai client STOMP trong test, A gửi, B nhận trong vòng 2 giây; B ngắt kết nối rồi A gửi → consumer không lỗi, offset vẫn tiến.
- **Hoàn thành khi:** người nhận offline **không** gây ra lỗi hay retry nào trong log. Đây là chỗ dễ làm sai nhất.

### Bước 7 — Con trỏ đọc, unread, đồng bộ nhiều thiết bị

- **Kết quả:** `POST /read` với điều kiện chỉ tăng; unread trong `GET /conversations`; event `READ_UPDATED` đẩy tới các session khác của chính user.
- **Kiểm chứng:** hai session của cùng một user, session 1 gọi `/read` → session 2 nhận `READ_UPDATED`; gọi `/read` với `seq` nhỏ hơn hiện tại → con trỏ **không** lùi.
- **Hoàn thành khi:** unread luôn bằng `last_seq - last_read_seq` sau mọi chuỗi thao tác trong test.

### Bước 8 — Lấy bù sau reconnect

- **Kết quả:** đường `afterSeq` hoàn chỉnh; tài liệu hợp đồng client (6 điểm cuối [04](04-du-lieu-va-api.md)).
- **Kiểm chứng:** B ngắt kết nối, A gửi 30 tin, B kết nối lại và gọi `afterSeq` → nhận đúng 30 tin, đúng thứ tự, không trùng.
- **Hoàn thành khi:** sau một chu kỳ ngắt–gửi–kết nối lại, trạng thái client **giống hệt** client không hề ngắt kết nối.

### Bước 9 — Thêm/xóa thành viên

- **Kết quả:** `POST /members`, `DELETE /members/{userId}`, `POST /leave`, event `MembershipChanged`.
- **Kiểm chứng:** xóa C trong lúc A đang gửi tin (test đồng thời) → C không nhận tin mới, A gửi bình thường; C gọi API sau đó → `404`.
- **Hoàn thành khi:** test đồng thời xóa-trong-lúc-gửi chạy xanh, và tài liệu ghi rõ khe hở "đã gửi frame trước khi xóa" ([02](02-kien-truc.md) luồng 6).

### Bước 10 — Lỗi và vận hành

- **Kết quả:** `DefaultErrorHandler` + backoff + DLT; phân loại lỗi retry được / không retry được; metric gồm số dòng outbox chưa publish, tuổi dòng cũ nhất, số kết nối WebSocket, số bản ghi DLT; job dọn outbox cũ.
- **Kiểm chứng:** bơm một event hỏng → vào DLT **không** retry; tạm dừng DB → consumer retry theo backoff rồi vào DLT; xem metric qua `/actuator/metrics`.
- **Hoàn thành khi:** có cảnh báo cho outbox tồn đọng và cho DLT khác rỗng. Thiếu hai cảnh báo này thì mọi lỗi ở [05](05-rui-ro-va-gioi-han.md) đều là lỗi âm thầm.

### Bước 11 — Đo tải (trước khi nói "xong")

- **Kết quả:** script k6/Gatling mở 1.600 kết nối và bơm 33 tin/s trong 10 phút; báo cáo ba phép đo ở mục trên.
- **Kiểm chứng:** heap ổn định (không tăng đơn điệu), p95 độ trễ gửi-tới-nhận, outbox không tồn đọng.
- **Hoàn thành khi:** có con số thật ghi lại trong tài liệu, kèm cấu hình máy đã dùng. Trước bước này, **không** phát biểu gì về việc chịu được bao nhiêu user.

### Sau MVP

| Bước | Kết quả | Kiểm chứng |
|------|---------|------------|
| 12 | Đính kèm file qua file-service | Gửi tin có `attachmentId`; người ngoài hội thoại không tải được |
| 13 | Push qua notification-service | Người nhận offline 30s mà chưa đọc thì nhận push; đang chat thì không |
| 14 | Thu hồi tin | Thu hồi xong, mọi thiết bị đều hiện "đã thu hồi" |
| 15 | Instance thứ hai (Cách 1 ở [03](03-kafka.md)) | Hai instance; client nối vào instance khác với người gửi vẫn nhận được tin |

---

## Thứ tự này có lý do

Ba điểm dễ bị làm ngược:

1. **Bước 3 (gửi tin) xong trước bước 5–6 (Kafka, WebSocket).** Có một giai đoạn hệ thống chat được mà không có realtime: gửi được, đọc được, chỉ phải tải lại trang. Nghe kém, nhưng nó tách **tính đúng của dữ liệu** khỏi **tính tức thời của đường truyền** — và mọi bug khó nhất đều nằm ở phần dữ liệu.
2. **Bước 5 có phép thử tắt Kafka.** Outbox tồn tại chỉ để xử lý đúng tình huống đó; không test thì sẽ phát hiện lúc production.
3. **Bước 11 (đo tải) là một bước thật, không phải việc làm thêm nếu rảnh.** Không có nó thì mọi con số trong tài liệu này vẫn chỉ là giả định.
