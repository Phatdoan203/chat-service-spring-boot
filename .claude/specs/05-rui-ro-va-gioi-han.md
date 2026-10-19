# 05 — Phân tích lỗi và giới hạn

## Bốn mốc không được đồng nhất với nhau

Trước khi đọc bảng dưới, phải phân biệt rạch ròi bốn câu sau. Chúng hay bị nói như một:

| Mốc | Nghĩa chính xác | Ai biết được | Có hiện lên UI? |
|-----|-----------------|--------------|-----------------|
| **Kafka đã nhận event** | Broker đã ghi bền vững vào partition | Chỉ producer | Không bao giờ |
| **Consumer đã xử lý** | Code fan-out đã chạy xong và commit offset | Chỉ consumer | Không bao giờ |
| **Thiết bị đã nhận** | Một frame WebSocket đã tới máy người nhận | Không ai, trừ khi thiết bị ack ngược lại | Không, vì MVP không làm delivered |
| **Người dùng đã đọc** | Người nhận chủ động gọi `POST /read` | Server, qua `last_read_seq` | **Có** |

"Consumer đã commit offset" là mốc dễ gây ngộ nhận nhất: nó nghĩa là *event đã được xử lý xong*, **không** nghĩa là có ai nhận được gì. Nếu người nhận đang offline thì fan-out là một việc không làm gì cả — và nó vẫn commit. Vì vậy **không bao giờ dùng Kafka để phục hồi tin cho client**; việc đó thuộc REST.

---

## Bảng tình huống lỗi

| # | Tình huống | Ảnh hưởng tới user | Dữ liệu còn ở đâu | Cách phục hồi |
|---|------------|--------------------|-------------------|---------------|
| 1 | **DB commit xong nhưng chưa phát được Kafka** (process chết, Kafka không với tới) | Người gửi thấy gửi thành công. Người nhận **không** thấy realtime và **không** có push | `message` đã commit; `outbox_event` có `published_at IS NULL` | Publisher quét lại và phát. Người nhận thấy tin ngay khi mở lại app (REST). **Tự động, không cần can thiệp** |
| 2 | **Kafka nhận event nhưng outbox chưa đánh dấu hoàn tất** | Người nhận có thể thấy tin **hai lần** nếu client không khử trùng | Event đã ở Kafka; outbox vẫn `NULL` | Lần quét sau publish lại, consumer khử theo `eventId`, client khử theo `messageId`. Không mất dữ liệu, chỉ trùng |
| 3 | **Consumer xử lý xong nhưng chưa commit offset** | Thiết bị nhận trùng tin sau khi service restart | Event ở Kafka; offset vẫn ở vị trí cũ | Xử lý lại, client khử theo `messageId`. Đây là **hệ quả cố ý** của at-least-once |
| 4 | **Consumer nhận event nhưng thiết bị đã ngắt kết nối** | Người nhận không thấy realtime — **đúng như mong đợi**, không phải lỗi | `message` trong DB | **Không retry, không báo lỗi, commit luôn.** Client lấy bù bằng `GET ...?afterSeq=` khi reconnect |
| 5 | **Server restart làm mất toàn bộ WebSocket** | Mọi client mất kết nối cùng lúc | Toàn bộ trong DB. Session chỉ trong RAM nên mất hẳn | Client tự reconnect (backoff ngẫu nhiên để tránh dồn cục), rồi `GET /conversations` + `afterSeq`. **Lưu ý:** event phát trong lúc restart đã bị commit offset nên Kafka không phát lại. Phục hồi hoàn toàn dựa vào REST |
| 6 | **Kafka ngừng hoạt động** | Gửi tin **vẫn thành công** (DB commit). Mất realtime và mất push. Client mở lại app vẫn thấy tin | `message` trong DB; `outbox_event` tồn đọng và phình dần | Khi Kafka lên, publisher xả outbox. Cần giới hạn tốc độ xả (đừng đẩy 100k event một lúc) và **cảnh báo khi số dòng chưa publish vượt ngưỡng** |
| 7 | **Event trùng hoặc đến sai thứ tự** | Tin hiện hai dòng, hoặc thứ tự hiển thị sai | DB vẫn đúng — `seq` là nguồn sự thật | Client khử theo `messageId`, sắp xếp theo `seq`, thấy hở `seq` thì gọi `afterSeq`. **DB không bao giờ sai thứ tự**, chỉ đường truyền sai |
| 8 | **Client quá chậm, buffer gửi bị đầy** | **Chỉ client đó** bị đóng kết nối | DB nguyên vẹn | `setSendBufferSizeLimit` + `setSendTimeLimit` đóng session đó; client reconnect và lấy bù. Không có giới hạn này thì một tab treo có thể ăn hết heap và làm **mọi** user chậm |
| 9 | **User đoán được ID hội thoại của người khác** | Không truy cập được gì | — | Ba lớp: UUID v4 không đoán được; mọi endpoint kiểm membership `ACTIVE` trước khi làm gì khác; trả **404** chứ không 403. Xem mục riêng bên dưới |
| 10 | **Hai instance cùng quét outbox** (khi mở rộng) | Mọi event phát hai lần; thứ tự outbox có thể sai | DB vẫn đúng | Chưa xảy ra với một instance. Khi mở rộng: `SELECT ... FOR UPDATE SKIP LOCKED`, hoặc chỉ một instance bật publisher qua cờ cấu hình |
| 11 | **Outbox có dòng publish hoài không được** (payload hỏng) | Một tin không bao giờ tới realtime | `message` vẫn đúng trong DB | Cột `attempts`: vượt 10 thì ghi cảnh báo và bỏ qua dòng đó để **không chặn các dòng sau**. Đây là lỗi phải có người xem |
| 12 | **Partition bị nghẽn vì retry một event lỗi** | Mọi hội thoại cùng partition đó **đứng** realtime | DB đúng | Phân loại lỗi đúng ngay từ đầu (xem [03](03-kafka.md)): lỗi vĩnh viễn vào DLT ngay, không retry |

### Điểm quan trọng nhất của cả bảng

Dòng 1 và dòng 6 cho thấy một tính chất đã thiết kế có chủ đích: **gửi tin không phụ thuộc Kafka.** Kafka chết thì mất realtime và mất push, nhưng **không mất tin và không chặn người gửi**. Đó là hệ quả của việc lưu DB trước rồi mới phát event ([ADR-0002](adr/0002-outbox-va-fanout-qua-kafka.md)).

Dòng 4, 5 và 7 đều dẫn về cùng một kết luận: **REST là đường phục hồi duy nhất.** Kafka không phải nơi client lấy lại tin.

---

## Chống việc đoán ID hội thoại

Lỗ hổng rẻ nhất để tạo ra và đắt nhất để sửa, nên tách riêng:

| Lớp | Việc |
|-----|------|
| ID không đoán được | `uuid` v4 cho `conversation.id` và `message.id`. **Không** dùng `bigserial` cho id lộ ra ngoài — số tăng dần thì thử `id+1` là ra hội thoại của người khác |
| Kiểm quyền ở mọi đường vào | Mọi endpoint có `conversationId` **phải** `SELECT` membership `ACTIVE` trước khi làm gì khác. Không ngoại lệ, kể cả endpoint chỉ đọc |
| Không tiết lộ sự tồn tại | Trả `404` khi không phải thành viên. `403` là nói với người ngoài rằng hội thoại đó có thật |
| Danh tính không từ client | `senderId` lấy từ header gateway, **không bao giờ** từ body. Đọc từ body nghĩa là ai cũng gửi tin dưới tên người khác được |
| Fan-out cũng phải kiểm | Consumer đọc danh sách thành viên từ DB, không tin danh sách trong event |

Ba test bắt buộc, viết từ bước đầu chứ không để sau:

1. `GET /conversations/{id}/messages` với id của hội thoại mình không thuộc → **404**.
2. `POST /conversations/{id}/messages` với id đó → **404**, và DB không có dòng nào mới.
3. `POST` với `X-User-Id` tự đặt của người khác → không mạo danh thành công.

### Lỗ hổng phải bàn với người làm gateway

`X-User-Id` là header thường, ai cũng tự đặt được. Toàn bộ bảo mật ở đây đứng trên một giả định: **gateway xóa (strip) header này từ request bên ngoài rồi chèn lại giá trị thật, và chat-service không nhận request nào không đi qua gateway.**

Nếu một trong hai điều đó không đúng thì ai cũng mạo danh được bất kỳ ai bằng một dòng `curl`. Đây là giả định yếu nhất về bảo mật trong cả thiết kế, và nó **không** nằm trong tầm kiểm soát của chat-service. Phải xác nhận với người làm gateway và ghi vào tài liệu vận hành:

- Gateway strip mọi header `X-User-*` đến từ bên ngoài.
- chat-service chỉ nghe trên mạng nội bộ, hoặc có mTLS / allowlist IP với gateway.
- Nếu không bảo đảm được: chat-service phải tự verify JWT, và khi đó nó cần khóa công khai của hệ thống danh tính. Thêm việc, nhưng không có cách nào khác.

---

## Giới hạn đã biết của thiết kế này

Những điều **sẽ** xảy ra, không phải rủi ro — nên nói trước:

| Giới hạn | Hệ quả | Khi nào phải xử lý |
|----------|--------|--------------------|
| Một instance | Restart là mọi người mất kết nối vài giây; không có HA | Khi SLA không cho phép rớt kết nối lúc deploy |
| Session chỉ trong RAM | Không biết ai đang online sau restart | Khi cần presence thật ("đang hoạt động") |
| Mọi tin trong một hội thoại ghi tuần tự | Một hội thoại không vượt được khoảng vài trăm tin/giây | Không bao giờ, với chat giữa người với người |
| Không có delivered | Không trả lời được câu "nó nhận được chưa" | Khi nghiệp vụ cần bằng chứng đã nhận |
| Xóa thành viên không tức thì | Người bị xóa có thể đã thấy tin gửi ngay trước đó | Không xử lý được bằng kỹ thuật — phải nói rõ với nghiệp vụ |
| Outbox trễ vài trăm ms | Realtime chậm hơn so với push trực tiếp sau commit | Khi cảm thấy chậm; lúc đó cân nhắc fast-path local |
| Không giới hạn tốc độ gửi | Một người có thể spam | Nên đặt rate limit ở gateway **trước khi** lên production |

Một việc nên làm ngay dù chưa cần: giới hạn độ dài `body` (ví dụ 4.000 ký tự), kiểm ở cả API lẫn DB. Không có giới hạn thì sớm muộn có người dán cả file log vào chat và làm vỡ luôn danh sách hội thoại.
