# ADR-0001: Gửi tin bằng REST, WebSocket chỉ để nhận

**Ngày**: 2026-10-15
**Trạng thái**: proposed
**Người quyết định**: chủ dự án (chờ chốt)

## Bối cảnh

Hệ thống cần gửi tin realtime. WebSocket đã có trong `pom.xml` và sẽ dùng để đẩy tin xuống client. Câu hỏi là thao tác **gửi** nên đi đường nào: HTTP POST, hay chính kết nối WebSocket đó. Người triển khai là dev Java khoảng hai năm kinh nghiệm, phải tự test và tự giải thích được. Auth đã xử lý ở gateway theo chuẩn HTTP.

## Quyết định

Gửi tin, đánh dấu đã đọc và mọi thao tác quản lý đều qua **REST**. WebSocket **chỉ một chiều**: server đẩy xuống client. Client không gửi gì qua WebSocket ngoài heartbeat.

## Phương án đã xem xét

### Phương án 1: Gửi tin qua WebSocket

- **Lợi**: ít hơn một vòng kết nối, độ trễ thấp hơn vài ms. Chỉ một kênh duy nhất với server.
- **Hại**: phải tự thiết kế khung ack và báo lỗi (correlationId, timeout, retry). Khó test vì mọi test cần một client STOMP. Gateway không rate-limit hay log được theo nội dung frame. Mất toàn bộ mã lỗi HTTP có sẵn.
- **Vì sao loại**: lợi ích duy nhất là vài ms mà người dùng không cảm nhận được, còn chi phí là **xây lại cơ chế lỗi và retry mà HTTP đã có**.

### Phương án 2: Hai chiều cùng lúc (REST và WebSocket đều gửi được)

- **Lợi**: client chọn đường nào cũng được.
- **Hại**: hai đường vào cho cùng một nghiệp vụ, hai nơi kiểm quyền, hai nơi có thể lệch nhau khi sửa code.
- **Vì sao loại**: không ai cần hai đường, và đây là nguồn bug âm thầm kinh điển.

## Hệ quả

### Tích cực

- Mã lỗi HTTP dùng được ngay: `404` khi không phải thành viên, `422` khi body sai, `200` khi retry trùng.
- Test bằng `MockMvc` và `curl`; phần lớn test không cần client STOMP.
- Gateway rate-limit, log và WAF hoạt động bình thường.
- Phần WebSocket chỉ còn một việc là đẩy frame, nên nhỏ và dễ thay thế.

### Tiêu cực

- Thêm một vòng HTTP cho mỗi tin (vài ms trong mạng nội bộ).
- Client phải giữ cả hai kênh: HTTP để gửi, WebSocket để nhận.

### Rủi ro

- Người gửi nhận tin của chính mình **hai lần** (response 201 và frame WebSocket). Giảm nhẹ: buộc client khớp theo `clientMessageId`, và ghi vào hợp đồng client ở [../04-du-lieu-va-api.md](../04-du-lieu-va-api.md).
