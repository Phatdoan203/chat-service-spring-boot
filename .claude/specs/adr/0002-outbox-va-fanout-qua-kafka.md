# ADR-0002: Lưu DB trước và dùng outbox, fan-out qua Kafka consumer

**Ngày**: 2026-10-15
**Trạng thái**: proposed
**Người quyết định**: chủ dự án (chờ chốt)

## Bối cảnh

Khi một tin được gửi, có bốn việc phải xảy ra: lưu bền vững, trả lời người gửi, đẩy tới người nhận đang online, và cho notification-service biết. Với **một instance**, WebSocket session nằm cùng JVM với transaction ghi, nên về kỹ thuật có thể đẩy trực tiếp sau commit mà không cần Kafka. Đề bài yêu cầu dùng Kafka, nên phải quyết định Kafka nằm ở đâu trong luồng này.

## Quyết định

1. **Lưu DB trước** trong một transaction, và trả `201` cho người gửi ngay sau commit.
2. Trong **cùng** transaction đó, ghi một dòng `outbox_event`.
3. Một job `@Scheduled` đọc outbox và publish sang Kafka.
4. **Fan-out WebSocket đi qua Kafka consumer**, kể cả khi consumer nằm trong cùng instance — không đẩy trực tiếp sau commit.

## Phương án đã xem xét

### Phương án 1: Đẩy trực tiếp vào WebSocket sau commit, Kafka chỉ cho service khác

- **Lợi**: độ trễ thấp nhất. Kafka chết thì realtime vẫn chạy, chỉ mất push — **bền hơn** phương án đã chọn ở đúng điểm này.
- **Hại**: hai đường code cho cùng một việc (một local, một qua Kafka cho service khác). Khi thêm instance thứ hai, đường local chỉ phục vụ được session của chính instance đó nên phải viết lại phần fan-out.
- **Vì sao loại**: hai nhánh code cho một nghiệp vụ sẽ lệch nhau khi sửa, và lệch theo kiểu khó phát hiện (một nhánh gửi, nhánh kia quên). Đây là phương án **mạnh thứ hai**: nếu độ trễ thành vấn đề thật thì nó là đường rút.

### Phương án 2: Nhận lệnh gửi qua Kafka rồi mới lưu DB

- **Lợi**: hấp thụ được đợt tải ghi lớn.
- **Hại**: API chỉ trả được `202`; người gửi không biết mình có quyền gửi không và không biết `seq`; kiểm quyền thành bất đồng bộ nên báo lỗi muộn qua một kênh khác.
- **Vì sao loại**: ở mức ~33 tin/s thì DB không phải cổ chai, nên đây là đổi sự phức tạp lớn lấy một lợi ích không cần đến.

### Phương án 3: Publish Kafka ngay trong transaction, không dùng outbox

- **Lợi**: ít một bảng, ít một job.
- **Hại**: không có transaction chung giữa DB và Kafka. Commit DB xong rồi process chết trước khi publish thì event mất **vĩnh viễn và âm thầm**. Publish trước commit thì có thể phát event cho một tin bị rollback — tệ hơn.
- **Vì sao loại**: đây đúng là lỗi mà outbox tồn tại để xử lý.

## Hệ quả

### Tích cực

- Gửi tin **không phụ thuộc Kafka**: Kafka chết thì vẫn gửi và đọc được tin, chỉ mất realtime và push.
- Một đường duy nhất tới mọi người nhận, nên không có hai nhánh code lệch nhau.
- Thêm consumer mới (audit, bot, thống kê) không phải sửa chat-service.
- Lên instance thứ hai không phải viết lại fan-out — xem [ADR-0004](0004-mot-instance-truoc.md).

### Tiêu cực

- Thêm độ trễ: tin đi DB → outbox → Kafka → consumer → WebSocket. Với chu kỳ quét outbox 200ms thì thêm khoảng 200–400ms.
- Thêm một bảng, một job publish, và một job dọn bảng đó.
- **Kafka chết là mất realtime**, trong khi phương án 1 thì không. Đây là cái giá thật của quyết định này.

### Rủi ro

- Outbox tồn đọng khi Kafka chết lâu. Giảm nhẹ: metric số dòng chưa publish, cảnh báo theo ngưỡng, giới hạn tốc độ khi xả.
- Publish trùng khi đánh dấu `published_at` thất bại. Giảm nhẹ: khử trùng ở consumer theo `eventId` và ở client theo `messageId`. Outbox là at-least-once, **không** exactly-once.
- Một dòng outbox hỏng chặn các dòng sau. Giảm nhẹ: cột `attempts`, vượt 10 thì bỏ qua và ghi cảnh báo.
