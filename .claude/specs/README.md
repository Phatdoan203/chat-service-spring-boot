# Specs — chat-service

Tập specs này trả lời `de_bai.md`. Chưa có code nghiệp vụ nào được viết.

| File | Nội dung | Mục de_bai |
|------|----------|------------|
| [01-nghiep-vu.md](01-nghiep-vu.md) | Use case MVP / bổ sung sau / chưa cần, 11 câu hỏi nghiệp vụ | §2 |
| [02-kien-truc.md](02-kien-truc.md) | 2 phương án + phương án chọn, 6 luồng tuần tự | §3 |
| [03-kafka.md](03-kafka.md) | Topic, key, partition, offset, retry, 4 lớp thứ tự | §4 |
| [04-du-lieu-va-api.md](04-du-lieu-va-api.md) | Bảng, index, transaction, REST + WebSocket, ví dụ payload | §5 |
| [05-rui-ro-va-gioi-han.md](05-rui-ro-va-gioi-han.md) | Bảng tình huống lỗi và cách phục hồi | §6 |
| [06-tai-va-ke-hoach.md](06-tai-va-ke-hoach.md) | Ước lượng tải, 12 bước triển khai | §7 |
| [adr/](adr/README.md) | 5 quyết định kiến trúc kèm phương án bị loại | xuyên suốt |

---

## Tôi hiểu bài toán thế nào

Xây **chat-service** cho nền tảng đối tác/cộng tác viên (CTV): chat 1–1 và chat nhóm, realtime qua
WebSocket, dữ liệu trên PostgreSQL, Kafka để tách các việc chậm ra khỏi đường gửi tin và để
service khác (notification) dùng được. Auth/authz danh tính đã xong ở gateway — chat-service chỉ
kiểm **quyền nghiệp vụ**: "user này có phải thành viên đang hoạt động của hội thoại này không".

Ràng buộc quan trọng nhất không phải quy mô mà là **một instance và không có Redis**. Hai thứ này
quyết định gần như toàn bộ thiết kế: WebSocket session nằm trong bộ nhớ của đúng một JVM, nên không
cần bất kỳ cơ chế định tuyến session nào — nhưng cũng không được thiết kế phụ thuộc vào điều đó,
vì instance thứ hai sẽ phá vỡ nó.

Mục tiêu là phương án một dev Java ~2 năm tự làm, tự test và tự giải thích được. Vì vậy mọi chỗ có
hai cách làm, tôi chọn cách **ít khái niệm hơn**, và ghi lại cách kia trong ADR.

---

## Quan sát được trong source (không phải giả định)

- `pom.xml`: Spring Boot parent **4.1.1**, Java **21**. Starter: `data-jpa`, `flyway`, `kafka`,
  `webmvc`, `websocket`; `flyway-database-postgresql`; `postgresql` (runtime); Lombok;
  `springdoc-openapi-starter-webmvc-ui` **3.1.0**. Test starter đầy đủ cho cả 5 nhóm trên.
- Tên starter theo kiểu Spring Boot 4 (`spring-boot-starter-webmvc`, `*-test` riêng từng module).
- `src/main/resources/application.yaml`: **chỉ có** `spring.application.name: chat-service`.
  Chưa có datasource, chưa có cấu hình Kafka → chưa thể chạy được với JPA.
- `src/main/java/.../ChatServiceApplication.java`: chỉ có `@SpringBootApplication` + `main`.
  Chưa có package `domain`/`web`/`config` nào.
- Chưa có `src/main/resources/db/migration` → Flyway có trong classpath nhưng chưa có migration.
- Test duy nhất: `ChatServiceApplicationTests.contextLoads()`.
- Thư mục này **chưa phải git repo**. Có `.github/modernize/java-upgrade/hooks/` (script
  `recordToolUse.ps1`/`.sh`) — tooling nâng cấp Java, không liên quan nghiệp vụ chat.

Suy ra: dự án mới ở mức skeleton. Mọi điều về nghiệp vụ dưới đây là **giả định của tôi**, đã ghi rõ
từng chỗ, chưa được bạn xác nhận.

---

## 5 câu hỏi quan trọng nhất

Tôi vẫn phân tích tiếp với giả định mặc định, không dừng chờ trả lời. Nhưng 5 câu này nếu trả lời
khác đi thì thiết kế đổi thật, không chỉ đổi con số:

1. **1.000–5.000 là tổng tài khoản hay online đồng thời?**
   Tổng tài khoản → ~1.600 kết nối WebSocket lúc cao điểm, một instance thừa sức.
   Online đồng thời → ~8.000 kết nối, phải đo heap/file descriptor và tính tới instance thứ hai
   ngay từ MVP. Xem [06](06-tai-va-ke-hoach.md). *Mặc định tôi dùng: tổng tài khoản.*

2. **Chat 1–1 có cần quan hệ nghiệp vụ không, hay ai cũng nhắn được cho ai?**
   Nếu cần (CTV chỉ nhắn được trưởng nhóm của mình và support) thì chat-service phải gọi sang
   service tổ chức/nhân sự để kiểm tra khi **tạo** hội thoại — một dependency mới.
   *Mặc định tôi dùng: có kiểm tra quan hệ, CTV ↔ CTV không được nhắn trực tiếp.*

3. **Thành viên mới được thêm vào nhóm có xem lịch sử trước khi họ tham gia không?**
   Không xem → cắt lịch sử bằng `join_seq`, đơn giản và rẻ. Được xem → phải quyết định thêm
   "xem từ lúc nhóm tạo hay từ mốc nào", và unread tính khác.
   *Mặc định tôi dùng: không xem lịch sử trước khi tham gia.*

4. **MVP có cần biết "ai đã đọc" trong nhóm, hay chỉ cần số tin chưa đọc của chính tôi?**
   Chỉ cần unread của mình → một cột `last_read_seq`, tính O(1).
   Cần danh sách người đã đọc → phải đọc chéo toàn bộ thành viên mỗi lần render.
   *Mặc định tôi dùng: chỉ unread của chính mình.*

5. **File đính kèm: file-service trả URL có thời hạn, hay chat-service phải kiểm quyền mỗi lần tải?**
   Quyết định ai là người chặn user ngoài hội thoại tải file. Nếu file-service không hỗ trợ
   URL có hạn thì chat-service phải làm proxy tải file — việc nặng và khác hẳn.
   *Mặc định tôi dùng: file-service cấp URL có hạn, chat-service chỉ lưu `attachment_id`.*

---

## Giả định yếu nhất (chỗ dễ sai nhất)

Xếp theo mức độ "nếu sai thì phải sửa thiết kế, không chỉ sửa code":

1. **Fan-out tin realtime đi qua Kafka rồi quay lại cùng instance** ([ADR-0002](adr/0002-outbox-va-fanout-qua-kafka.md)).
   Đổi lấy một đường code duy nhất, phải trả bằng độ trễ thêm và bằng việc Kafka chết thì mất
   realtime. Nếu bạn ưu tiên độ trễ thấp nhất, đổi sang push trực tiếp sau commit.
2. **Người bị xóa khỏi nhóm mất luôn quyền xem lịch sử.** Đơn giản nhất, nhưng nhiều hệ thống
   thật cho xem lại phần lịch sử tới lúc rời. Đây là quyết định nghiệp vụ, không phải kỹ thuật.
3. **Không làm trạng thái "đã nhận" (delivered) trong MVP.** Nếu UI đã thiết kế 3 dấu tick thì
   giả định này sai và phải thêm bảng per-device.
4. **Gateway chèn danh tính user vào header và chặn client tự chèn.** Nếu gateway không bảo đảm
   được điều đó thì chat-service phải tự verify token — đổi hẳn phần bảo mật.

## Yêu cầu còn thiếu / mâu thuẫn trong đề bài

- Đề yêu cầu **kiến trúc microservice** nhưng cũng yêu cầu **một instance** và **không tách
  realtime-service**. Hai điều này không mâu thuẫn, nhưng nghĩa là MVP thực chất là một service
  đơn lẻ nói chuyện với 2 service có sẵn — nên gọi đúng tên như vậy thay vì gọi là microservice.
- Đề yêu cầu dùng Kafka, nhưng với **một instance** thì Kafka **không cần thiết** cho việc đẩy tin
  tới người nhận. Tôi vẫn giữ Kafka và nêu rõ nó giải quyết việc gì thật ([02](02-kien-truc.md#kafka-giải-quyết-gì-và-không-giải-quyết-gì)) —
  nếu không thì đó là công nghệ dùng cho có.
- Chưa có thông tin về **giữ dữ liệu bao lâu** (retention) và **xuất/xóa theo yêu cầu người dùng**.
  Với nền tảng có hợp đồng đối tác, phần này thường bắt buộc và ảnh hưởng tới schema.
