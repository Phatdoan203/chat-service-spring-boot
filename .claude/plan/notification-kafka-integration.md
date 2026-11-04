# Kế hoạch: tích hợp notification qua Kafka (chat-service → processor)

**Ngày**: 2026-11-04 · **Trạng thái**: đã qua advisor (APPROVE WITH CHANGES) · **Phạm vi**: phía chat-service.
Yêu cầu phải làm ở processor: xem `processor-requirements.md` (file cùng thư mục).

## 1. Mục tiêu và quyết định đã chốt

Khi một tin được tạo, báo cho **mọi thành viên ACTIVE trừ người gửi**, qua service `processor`
(đã có sẵn, nằm ở `E:\kafka-project\processor`) — nó nhận event Kafka rồi gửi notification.

Hai loại notification người dùng yêu cầu:

| Loại | Kênh processor | recipient | Trạng thái |
|---|---|---|---|
| **web** (in-app) | `INAPP` | `userId` | **Cần thêm ở processor** (type mới). Web client đọc qua REST của processor. |
| **app** (push) | `PUSH` | `userId` (placeholder) | Processor **không cần sửa** — `PushStrategy` là stub, log rồi `SENT`. |

Quyết định của chủ dự án:

- **Bắn mọi tin cho mọi người nhận** (trừ sender). Chưa làm logic "chờ 30s + kiểm chưa đọc" của
  spec 03 — để lại làm sau, điểm móc vào là `NotificationDispatcher`.
- Web realtime vẫn do WebSocket sẵn có của chat-service lo (`ChatFanout`), **không đi qua processor**.
  Notification qua processor là kênh bổ sung (in-app center + push).

## 2. Luồng — đúng ADR-0002/0004/0005

```
send() [transaction]                       đã có, KHÔNG sửa
  └─ INSERT message + nextSeq + outbox_event("MessageCreated")   (MessageServiceImpl:96)

OutboxPublisher  @Scheduled, 1 luồng       (MỚI)
  └─ đọc outbox_event chưa publish ("MessageCreated")
     → NotificationService.buildFor(): member ACTIVE trừ sender, render content, dựng NotificationEvent
     → mỗi recipient × kênh → publish thẳng:
          NotificationEvent(PUSH)  → topic push-events
          NotificationEvent(INAPP) → topic inapp-events   (khi inapp-enabled)
     → set published_at

processor (đã có) consume push-events / inapp-events → gửi / lưu → notification.result
```

**Quyết định 2026-11-04 (chủ dự án): bỏ hop qua topic `chat.message.created.v1` + consumer.**
Bản kế hoạch gốc tách 2 hop (outbox → `chat.message.created.v1` → dispatcher) để có "event bus" dùng
chung cho WS fan-out + audit sau này (ADR-0002). Nhưng **WS fan-out đã do `ChatFanout` xử lý trong code**,
và hiện tại topic chat chỉ có đúng một consumer là dispatcher — nên hop đó là thừa cho nhu cầu *bây giờ*.

Rút về **một hop**: `OutboxPublisher` đọc outbox rồi bắn thẳng notification sang processor.

- Vẫn giữ **outbox** nên vẫn đúng ADR-0005 (không publish Kafka trong transaction gửi tin) và ADR-0002
  phần cốt lõi (không mất event khi process chết sau commit). Chỉ bỏ chặng Kafka trung gian.
- Logic bung-member + render nằm ở `NotificationService` (tầng service), publisher chỉ orchestrate +
  publish (ngoài transaction). Không nhét nghiệp vụ vào messaging.
- **Đánh đổi**: không còn stream `chat.message.created.v1` dùng chung. Khi nào cần WS-qua-Kafka (thay
  `ChatFanout`) hoặc audit/analytics thì dựng topic đó lại — lúc đó `OutboxPublisher` publish lên topic
  chat và các consumer (gồm notification) đọc từ đó. Đây là đường quay lại ADR-0002 đầy đủ.

## 3. Thành phần MỚI ở chat-service

Tất cả nằm đúng cây thư mục hiện có (`messaging/`, `config/`, `repository/`, `dto/response/`).
Không tạo package feature mới.

### 3.1 Producer Kafka — chỉ qua autoconfig (KHÔNG có class config riêng)
Thiết kế 1 hop chỉ cần **producer**, nên không còn `KafkaConfig.java`, không consumer, không error handler,
không `NewTopic` bean. Producer lấy từ `spring.kafka.producer.*` trong `application.yaml`:
`acks=all`, `enable.idempotence=true`, `retries` cao, key+value `StringSerializer`. `KafkaTemplate<String,String>`
do autoconfig tạo. Value là **chuỗi JSON** tự build (xem 3.5) để kiểm soát wire format.

> Topic `push-events` / `inapp-events` là input của processor — ở dev broker auto-create (1 partition).
> Production thì processor/ops tạo tường minh. chat-service không khai báo topic của processor.

### 3.2 `OutboxPublisher.java` (messaging/) — relay notification
- `@Scheduled(fixedDelay)` ~200ms. Thêm `@EnableScheduling` (chưa có trong project).
- Đọc batch `outbox_event WHERE published_at IS NULL ORDER BY id`. Với mỗi dòng `MessageCreated`:
  parse payload → `NotificationService.buildFor()` → publish từng `NotificationEvent` sang
  `push-events`/`inapp-events` (key=recipient), chặn `.get()` xác nhận → `set published_at`.
  Dòng eventType khác thì markPublished bỏ qua (không chặn dòng sau).
- **Chống race nhiều instance (advisor điểm #1 — lỗ lớn nhất)**: `@Scheduled` chỉ một luồng *trong một
  JVM*, không chặn *nhiều instance*. ADR-0004 dự tính scale nhiều instance. Hai publisher cùng quét
  sẽ publish trùng (chấp nhận được, processor khử theo eventId tất định — xem 3.4).
  - **MVP (một instance)**: gate publisher bằng cờ config `chat.outbox.publisher.enabled=true`
    (mặc định bật) — chỉ bật ở đúng một instance. Ghi rõ đây là shortcut đúng theo ADR-0004
    "một instance trước": `// shortcut: publisher chay 1 instance; khi scale -> claim FOR UPDATE SKIP LOCKED hoac leader election`.
  - **Khi scale**: chuyển sang claim bằng `SELECT ... FOR UPDATE SKIP LOCKED` (hoặc
    `UPDATE ... RETURNING`) + advisory lock/leader election. Lưu ý SKIP LOCKED với nhiều claimer vẫn
    có thể xen kẽ thứ tự giữa các dòng cùng conversation — nếu cần thứ tự tuyệt đối thì phải leader
    election, không chỉ SKIP LOCKED.
- **Tombstone dòng độc (advisor #2)**: khi `attempts` vượt ngưỡng (vd 10), **set `published_at`**
  (hoặc chuyển DLT) + log một ERROR, để dòng đó **rời khỏi** tập `published_at IS NULL`. Nếu chỉ
  "skip" mà không tombstone, nó ở lại `outbox_event_unpublished_idx` (V1:126) mãi mãi và bị fetch lại
  ở đầu mỗi lần quét `ORDER BY id`, làm hỏng chính partial index đó.

### 3.3 `repository/OutboxEventRepository.java` (bổ sung)
- `@Query` native lấy batch chưa publish theo `id`.
- `@Modifying @Query` set `published_at = now()` theo id (và tăng `attempts` khi publish lỗi,
  tombstone khi vượt ngưỡng). **Không** mở rộng entity `OutboxEvent` thêm field published_at/attempts
  — giữ `insertable=false` hiện tại, cập nhật bằng native update (hợp coding-standards).

### 3.4 `service/NotificationService.java` (+ impl) — bung recipient + render
`buildFor(MessageCreatedEvent)` `@Transactional(readOnly=true)`, trả `List<NotificationEvent>`:
- Recipient: `members.findActiveUserIds(conversationId)` (đã có) rồi **loại senderId**.
- Mỗi recipient × kênh đang bật (`push-enabled` / `inapp-enabled`) → một `NotificationEvent`
  (`recipient = userId`).
- **eventId tất định** = `UUID.nameUUIDFromBytes((messageId + ":" + userId + ":" + channel).getBytes(UTF_8))`.
  At-least-once (publisher gửi lại khi chưa kịp markPublished) → processor khử trùng theo eventId. Không random.
- `referenceType="CHAT_MESSAGE"`, `referenceId=messageId`, `priority="NORMAL"`,
  `subject` = "Tin nhan moi tu <tên người gửi>" (tra `UserRepository`), `recipientName=null`.
- **content không được rỗng (advisor #3)**: processor bắt buộc `content` non-blank. Tin IMAGE/FILE có
  `body = null` (MessageServiceImpl.send:63-65). Render fallback: TEXT → preview body (cắt ngắn 200),
  IMAGE → "Da gui mot anh", FILE → "Da gui mot tep".
- **Không log** `body`/preview ở bất kỳ mức nào. `OutboxPublisher` (messaging) chỉ log
  `event=notification.dispatched` với id, không log nội dung.

Publisher (3.2) gọi `buildFor` **ngoài** mọi transaction nghiệp vụ rồi mới publish — không vi phạm
"không publish Kafka trong transaction". Lỗi produce: tạm thời (broker down/timeout) → dừng sweep, thử
lại cả dòng tick sau; vĩnh viễn (RecordTooLargeException/SerializationException) → log + bỏ qua recipient.

### 3.5 `dto/response/NotificationEvent.java` (record MỚI, outbound — khớp đúng processor)
Đúng **11 field** processor cần, không dư field (Spring Boot default có bỏ qua field lạ, nhưng vẫn
gửi sạch): `eventId, occurredAt, schemaVersion, type, recipient, recipientName, subject, content,
referenceId, referenceType, priority`.

- **`schemaVersion` LUÔN = 1, gửi tường minh**: processor dùng Lombok `@Builder.Default=1` + `@NoArgsConstructor`
  nên JSON thiếu field này sẽ deserialize ra `0` → bị loại `UNSUPPORTED_SCHEMA_VERSION`.
- **`occurredAt` (advisor #4)**: processor parse `LocalDateTime` **không timezone**. Phải phát ISO-8601
  local — `2026-11-04T09:21:44.512` — **không offset, không 'Z'**. Jackson 3 (`tools.jackson` của chat-service)
  và Jackson 2 (processor) khác default ngày; chuỗi có offset/'Z' hoặc dạng mảng timestamp sẽ làm
  deserializer processor **chết thẳng vào DLT của nó**, không phải lỗi validation sạch.
  → Serialize field này thành **String đã format sẵn**, value Kafka dùng `StringSerializer`, và **kiểm
  literal bằng tay một lần**. Hạn chế vốn có: "server zone" nhập nhằng giữa các container — đó là schema
  của processor, không sửa ở đây được; chỉ là **không** lấp bằng `Instant`.
- Build JSON bằng `ObjectMapper` (Jackson 3 sẵn có trong `MessageServiceImpl`) ra String rồi gửi.

### 3.6 `application.yaml` (bổ sung) — producer-only
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}   # cùng broker dev với processor
    producer:
      acks: all
      retries: 2147483647
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      properties: { enable.idempotence: true }
chat:
  outbox:
    publisher:
      enabled: ${OUTBOX_PUBLISHER_ENABLED:true}   # bật ở đúng 1 instance
      interval-ms: 200
  notification:
    push-enabled: true
    inapp-enabled: false     # bật khi processor đã thêm type INAPP
    topics:
      inapp: inapp-events
      push: push-events
```
Processor và chat-service dùng chung broker `localhost:9092` ở dev (xem docker-compose của processor:
listener EXTERNAL `localhost:9092`, auto-create topics bật). Không còn consumer nên không có
`spring.kafka.consumer`/`listener`.

## 4. Phân phase

| Phase | Việc | Test được gì |
|---|---|---|
| **1+2** | Producer (yaml) + OutboxPublisher relay + NotificationService + @EnableScheduling + kênh PUSH | Gửi tin → thấy record ở `push-events` (Kafka UI :8080), processor log "PUSH stub completed", row `notification` = SENT. |
| **3** | Kênh INAPP (**cần processor sửa trước** — xem processor-requirements.md) rồi bật `inapp-enabled=true` | Web lấy in-app noti qua `GET /api/notifications/history`. |
| sau | device token thật cho PUSH; consume `notification.result`; logic "chờ 30s + kiểm chưa đọc" (spec 03); dựng lại `chat.message.created.v1` nếu cần WS-qua-Kafka/audit | — |

## 5. Giới hạn đã biết (ghi rõ, không giấu)

- **PUSH phase 2 là SENT-nhưng-không-tới-đâu**: `PushStrategy` là stub, `app_user` chưa có device token
  (User.java không có phone/token). Row `notification` SENT **không** nghĩa là đã gửi tới thiết bị. Là
  smoke-test đường ống, đừng đọc nhầm là "đã giao".
- **body rời khỏi chat-service**: preview trong event nghĩa là nội dung tin **lưu sang DB processor**.
  Vốn có của tính năng preview — chấp nhận ranh giới này, và dispatcher tuyệt đối không log preview/body.
- **Broker chết lâu (> ~7s một tick + timeout)**: publisher dừng sweep, dòng outbox còn `published_at IS NULL`,
  tự gửi lại khi broker sống — không mất notification (khác với bản consumer-DLT trước đây). Đây là lợi
  điểm của việc bỏ consumer: không có đường DLT nào nuốt notification.
- Outbox là **at-least-once**, không exactly-once. Khử trùng ở processor (eventId tất định) và client (messageId).

## 6. Checklist (ĐÃ hiện thực Phase 1+2, 2026-11-04)

- [x] Producer yaml; `OutboxPublisher` relay (gate 1-instance, confirm-trước-markPublished, tombstone poison, phân loại lỗi tạm thời/vĩnh viễn).
- [x] `OutboxService(+Impl)`, `OutboxPendingView` (ở `dto/response`), repo queries (`findUnpublished`/`markPublished`/`recordFailure`), `@EnableScheduling`.
- [x] `NotificationService(+Impl)` (loại sender, eventId tất định, occurredAt String no-offset, schemaVersion=1, content fallback), `NotificationEvent` record. Kênh PUSH bật, INAPP tắt.
- [x] Biên dịch sạch (`./mvnw -DskipTests compile`, Corretto 21). Chưa chạy end-to-end (session không có broker/DB).
- [ ] Phase 3: sau khi processor xong INAPP → đặt `chat.notification.inapp-enabled=true`.
- [ ] `@Operation` springdoc cho endpoint mới nếu có (device-token registration ở phase sau).
