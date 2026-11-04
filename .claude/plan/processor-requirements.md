# Yêu cầu đối với processor (để chat-service tích hợp notification)

**Đích**: service `processor` ở `E:\kafka-project\processor` (Spring Boot 3.3.5, Java 17, schema `notification`).
**Người yêu cầu**: chat-service. Xem luồng tổng ở `notification-kafka-integration.md`.

Tóm tắt: chat-service sẽ **produce** `NotificationEvent` (JSON String) vào topic của processor khi có
tin mới. Hai kênh:

| Kênh | Topic | Processor cần làm gì |
|---|---|---|
| **PUSH** (app) | `push-events` | **Không cần gì** — đã có listener + `PushStrategy` (stub). |
| **INAPP** (web) | `inapp-events` | **Cần thêm mới** — processor hiện không có kênh in-app. |

## A. Hợp đồng event chat-service sẽ gửi (để processor không bất ngờ)

Đúng POJO `NotificationEvent` hiện tại của processor, value Kafka là **chuỗi JSON**, key là String:

```json
{
  "eventId": "<UUID tất định = nameUUIDFromBytes(messageId:userId:channel)>",
  "occurredAt": "2026-11-04T09:21:44.512",
  "schemaVersion": 1,
  "type": "INAPP",
  "recipient": "<userId của người nhận>",
  "recipientName": "<tên người gửi>",
  "subject": "<nhãn hội thoại / tên người gửi>",
  "content": "<preview: text cắt ngắn, hoặc 'Đã gửi một ảnh/tệp'>",
  "referenceId": "<messageId>",
  "referenceType": "CHAT_MESSAGE",
  "priority": "NORMAL"
}
```

- `eventId` **tất định** theo (messageId, userId, channel) → processor khử trùng theo `eventId`
  (`insertIfAbsent`) là đủ chống at-least-once. chat-service không gửi random.
- `occurredAt` phát dạng **ISO local, không offset, không 'Z'**. (chat-service đã lo đúng phía nó.)
- `recipient` của INAPP/PUSH là **`userId`** (chuỗi UUID). PUSH chưa có device token thật —
  dùng userId làm placeholder; vì `PushStrategy` là stub nên vẫn chạy.

## B. Việc PHẢI làm ở processor cho kênh INAPP

Processor hiện chỉ hỗ trợ `EMAIL/SMS/PUSH` (`SUPPORTED_TYPES` trong `NotificationIngestService`,
CHECK `chk_notifications_type` trong migration V3). Thêm INAPP gồm 4 thay đổi:

1. **Nới CHECK constraint `type`** bằng migration mới (vd `V7__add_inapp_type.sql`) — **không sửa migration cũ**:
   ```sql
   ALTER TABLE notification.notifications DROP CONSTRAINT IF EXISTS chk_notifications_type;
   ALTER TABLE notification.notifications
       ADD CONSTRAINT chk_notifications_type CHECK (type IN ('EMAIL','SMS','PUSH','INAPP'));
   ```
   (Cột `type` đã là `VARCHAR(20)`, đủ chỗ. Kiểm xem V4/V5/V6 có định nghĩa lại constraint này không
   rồi nhắm đúng bản mới nhất.)

2. **Thêm `INAPP` vào `SUPPORTED_TYPES`** (`NotificationIngestService.SUPPORTED_TYPES`), để event qua
   được `validate()`.

3. **Thêm topic input + listener** `inapp-events`:
   - `application.yaml`: `kafka.topics.input.inapp-events: inapp-events`.
   - `NotificationConsumer`: thêm `consumeInApp(...)` giống `consumeEmail`, gọi `consume(record, ack, "INAPP")`.

4. **Thêm `InAppStrategy implements NotificationStrategy`** (`getType()` trả `"INAPP"`):
   - Dispatch worker chọn strategy qua `Map` keyed theo `getType()` (`NotificationDispatchWorker`),
     thiếu bean là rơi `NO_STRATEGY` → FAILED.
   - In-app **không gửi ra ngoài**: `send()` chỉ cần log + để worker đánh `SENT` (có thể set
     `channelMessageId` = id nội bộ). Web client đọc qua REST sẵn có.

## C. Web client lấy in-app noti thế nào

Dùng REST **đã có** của processor — không phải làm mới:

```
GET /api/notifications/history   (theo recipient = userId, hoặc theo reference)
GET /api/notifications/status/{id}
```

Nếu cần đẩy realtime xuống web thì đó là WebSocket của **chat-service** (đã có), không phải việc của
processor. Processor chỉ là nơi lưu danh sách notification bền vững (notification center).

## D. Việc KHÔNG cần làm cho PUSH

- `push-events` listener + `PushStrategy` đã có. `PushStrategy` hiện là **stub**: log rồi coi như gửi
  thành công (`SENT`). Nên PUSH test được đường ống ngay, không cần client/FCM thật.
- **Lưu ý trả lại cho chat-service**: row PUSH `SENT` **không** nghĩa là đã tới thiết bị (stub + chưa
  có device token). Khi nào tích hợp FCM/APNs thật thì mới là giao thật.

## E. Hạ tầng / vận hành

- Dev: chung broker Kafka `localhost:9092` (docker-compose của processor, auto-create topics bật) nên
  `inapp-events`/`push-events` tự sinh khi chat-service gửi lần đầu. **Production**: tạo topic tường minh
  với số partition chủ đích (đừng để auto-create 1 partition).
- DLT của processor là `notification-dlt` (riêng, khác DLT của chat-service). Event sai hợp đồng
  (thiếu field, type lạ, `occurredAt` sai format) rơi vào đó — là tín hiệu chat-service gửi sai, báo lại.
- Topic `notification.result` processor đã publish kết quả. chat-service **chưa** consume (phase sau).
