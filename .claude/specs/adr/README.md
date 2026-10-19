# Architecture Decision Records — chat-service

| ADR | Quyết định | Trạng thái | Ngày |
|-----|------------|------------|------|
| [0001](0001-gui-tin-bang-rest.md) | Gửi tin bằng REST, WebSocket chỉ để nhận | proposed | 2026-10-15 |
| [0002](0002-outbox-va-fanout-qua-kafka.md) | Lưu DB trước + outbox, fan-out qua Kafka consumer | proposed | 2026-10-15 |
| [0003](0003-kafka-key-va-partition.md) | Kafka key = conversationId, 6 partition | proposed | 2026-10-15 |
| [0004](0004-mot-instance-truoc.md) | Một instance trước; mở rộng bằng consumer group riêng mỗi instance | proposed | 2026-10-15 |
| [0005](0005-cap-seq-per-conversation.md) | Cấp seq per-conversation bằng UPDATE RETURNING | proposed | 2026-10-15 |

Tất cả đang ở trạng thái **proposed**: chưa có code, và một số dựa trên giả định nghiệp vụ chưa được chốt (xem [../01-nghiep-vu.md](../01-nghiep-vu.md)). Chuyển sang `accepted` khi bắt đầu hiện thực bước tương ứng trong [../06-tai-va-ke-hoach.md](../06-tai-va-ke-hoach.md).

Quy ước: mỗi ADR nêu **phương án bị loại và lý do loại**. Nếu sau này đổi quyết định thì tạo ADR mới và đánh dấu ADR cũ là `superseded by ADR-NNNN`, không sửa nội dung cũ.
