# ADR-0004: Một instance trước; mở rộng bằng consumer group riêng mỗi instance

**Ngày**: 2026-10-15
**Trạng thái**: proposed
**Người quyết định**: chủ dự án (chờ chốt)

## Bối cảnh

Đề bài: ưu tiên một instance, **không dùng Redis**, không mặc định tách realtime-service. Với một instance thì mọi WebSocket session nằm trong bộ nhớ một JVM nên không có vấn đề định tuyến. Nhưng phải biết trước đường mở rộng, vì nếu code fan-out giả định "tôi là instance duy nhất" thì instance thứ hai sẽ phá vỡ nó.

Bài toán khi có nhiều instance: instance 1 consume được event, nhưng WebSocket của người nhận nằm ở instance 2, và không có Redis để làm pub/sub chung.

## Quyết định

MVP chạy **một instance**. Khi cần mở rộng, dùng cách **mỗi instance một consumer group riêng**: `group.id = chat-ws-fanout-${INSTANCE_ID}`. Mọi instance nhận mọi event, mỗi instance chỉ đẩy cho session nó đang giữ. **Chưa triển khai bây giờ.**

Chuẩn bị duy nhất phải làm từ đầu: không viết code fan-out dựa trên giả định một instance (ví dụ đếm tổng số session để báo cáo presence toàn hệ thống).

## Phương án đã xem xét

### Phương án 1: Sticky session ở load balancer theo userId

- **Lợi**: mỗi instance chỉ nhận event của user nó giữ, không xử lý thừa.
- **Hại**: phải đổi Kafka key sang `userId`, tức là **mất thứ tự trong hội thoại** ([ADR-0003](0003-kafka-key-va-partition.md)). Một tin nhiều người nhận nên vẫn phải phát nhiều bản. Instance restart là phải rebalance kết nối.
- **Vì sao loại**: trả bằng thứ tự tin để tiết kiệm CPU — đổi sai chiều.

### Phương án 2: PostgreSQL LISTEN/NOTIFY làm bus nội bộ

- **Lợi**: không thêm hạ tầng, dùng DB đang có.
- **Hại**: payload giới hạn khoảng 8KB; không bền (instance không nghe lúc đó thì mất); tạo **con đường thứ hai** song song với Kafka.
- **Vì sao loại**: thêm một cơ chế phát tán nữa trong khi đã có Kafka — đúng thứ [ADR-0002](0002-outbox-va-fanout-qua-kafka.md) đang tránh.

### Phương án 3: Tách realtime-service ngay từ MVP

- **Lợi**: scale phần kết nối độc lập; restart phần ghi không rớt kết nối.
- **Hại**: hai service, hai lần deploy, và **vẫn phải giải đúng bài định tuyến ở trên**, chỉ sớm hơn. realtime-service vẫn phải đọc DB để biết thành viên nên nó không hề không-state.
- **Vì sao loại**: đề bài không yêu cầu, và nó không làm bài toán khó biến mất.

## Hệ quả

### Tích cực

- MVP đơn giản nhất có thể: một deploy, một cấu hình, chạy được ở máy local.
- Đường mở rộng chỉ cần thêm một biến môi trường vào `group-id`; **code fan-out không đổi một dòng**.
- Không cần Redis, không cần broker relay, không cần sticky session.

### Tiêu cực

- Một instance là **điểm chết duy nhất**: restart hoặc deploy là mọi người mất kết nối vài giây.
- Khi mở rộng theo cách này, mỗi instance xử lý **toàn bộ** lưu lượng event, nên công việc tăng theo số instance. Với 5 instance và 33 tin/s thì tổng khoảng 165 lần xử lý/s — vẫn không đáng kể, nhưng không mở rộng vô hạn được.
- Mỗi instance có offset riêng, và consumer group của instance đã chết phải được dọn.

### Rủi ro

- Nếu con số 5.000 thực ra là **online đồng thời** thì một instance phải giữ khoảng 8.000 kết nối, và quyết định này cần xem lại ngay từ MVP (xem [../06-tai-va-ke-hoach.md](../06-tai-va-ke-hoach.md)). Đây là lý do câu hỏi đó được xếp quan trọng nhất.
- Có người sau này vô tình viết code giả định một instance. Giảm nhẹ: ghi vào `CLAUDE.md` và vào ADR này.
