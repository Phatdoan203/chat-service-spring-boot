# Architecture rules

## Chiều phụ thuộc — một chiều, không ngoại lệ

```
controller ──> service ──> repository ──> domain
messaging  ──> service
config, common  <── mọi tầng đều dùng được
```

- `service` **không** import `controller`.
- `repository` **không** import `service`.
- `controller` **không** import `repository`. Không có ngoại lệ "chỉ query đơn giản nên gọi tắt".
- `messaging` (Kafka consumer, outbox publisher) gọi vào `service`, không tự mở transaction nghiệp vụ.

Vi phạm hay gặp nhất là controller gọi repository cho một câu `GET` "cho nhanh". Lần thứ hai cần thêm điều kiện lọc thì logic đã nằm ở hai nơi.

## Trách nhiệm từng tầng

| Tầng | Làm | Không làm |
|---|---|---|
| `controller` | Nhận request, validate hình thức (`@Valid`), gọi 1 method service, trả DTO + HTTP status | Không `if` nghiệp vụ, không transaction, không gọi repository |
| `service` | Nghiệp vụ, `@Transactional`, kiểm membership, cấp `seq`, ghi outbox | Không biết gì về HTTP, không nhận `HttpServletRequest` |
| `repository` | Truy vấn | Không logic, không transaction |
| `domain` | Entity + enum, giữ invariant đơn giản | Không gọi service, không inject gì |
| `messaging` | Publish / consume, gọi service | Không chứa nghiệp vụ |

## Transaction

- **Chỉ `service` mở transaction.** `@Transactional` đặt trên method public của service, không đặt trên class.
- Mặc định `@Transactional(readOnly = true)` cho method đọc.
- **Không gọi mạng, không gọi service ngoài, không publish Kafka trong transaction.** Transaction gửi tin giữ khóa dòng `conversation` (ADR-0005); một cuộc gọi HTTP chậm sẽ làm cả hội thoại đứng. Việc gì cần làm sau commit thì ghi `outbox_event` rồi để publisher lo.
- Một request nghiệp vụ = một transaction. Không `REQUIRES_NEW` trừ khi có lý do viết thành comment.

## JPA

- **Không dùng quan hệ JPA** (`@ManyToOne`, `@OneToMany`). Entity giữ id thô: `UUID conversationId`, không phải `Conversation conversation`.
  Lý do: service này không bao giờ cần duyệt object graph — mọi truy vấn đều lọc theo id và trả projection. Bỏ quan hệ là bỏ luôn N+1, `LazyInitializationException`, và chuyện phải nhớ `fetch join`.
- Không có bảng user: `userId` là varchar thô trong các bảng chat. Interface projection `ConversationListView` nằm ở `dto/response/`.
- `@Id` do ứng dụng sinh (`UUID.randomUUID()`) hoặc để DB `DEFAULT gen_random_uuid()`. Không `@GeneratedValue(AUTO)`.
- Không `@Data` của Lombok trên entity — nó sinh `equals`/`hashCode` theo mọi field, sai với entity có id. Dùng `@Getter`, `@Setter` và constructor rõ ràng.
- Truy vấn phức tạp (danh sách hội thoại kèm unread, lấy bù theo `seq`) viết `@Query` native hoặc JPQL trả về **interface projection**, không lấy entity rồi map bằng Java.
- `spring.jpa.hibernate.ddl-auto: none` là bất biến. **Flyway là nguồn sự thật duy nhất của schema.**

## Flyway + Supabase

- Migration chỉ thêm file mới `V<n>__<ten>.sql`. **Không bao giờ sửa file migration đã chạy** — checksum lệch là Flyway chặn khởi động.
- Kết nối qua **cổng 5432** (direct), không dùng pooler 6543. Pooler của Supabase chạy transaction mode nên mỗi câu lệnh có thể rơi vào connection khác nhau, advisory lock mức session của Flyway không giữ được → migration treo hoặc lỗi.
- Nếu 5432 không kết nối được (project mới của Supabase chỉ có IPv6 cho direct connection) thì dùng **session-mode pooler**, không phải transaction mode.
- Không bật `flyway.clean` ở bất kỳ môi trường nào. Đây là Supabase thật, không phải container bỏ đi được.
- Không để mật khẩu DB trong `application.yaml`. Dùng biến môi trường.

## WebSocket

- STOMP trên `/ws/chat`, `SimpleBroker` trong bộ nhớ. Không cần Redis, không cần broker relay (ADR-0004).
- **Một chiều: server đẩy xuống.** Không có `@MessageMapping` nhận tin từ client. Gửi tin bằng REST (ADR-0001).
- Một đích duy nhất `/user/queue/chat`; client phân nhánh theo field `type`.
- Bắt buộc có 3 giới hạn, nếu không thì một tab treo có thể ăn hết heap: `setSendTimeLimit(10_000)`, `setSendBufferSizeLimit(512_000)`, `setMessageSizeLimit(64_000)`.
- Danh tính lấy từ `HandshakeInterceptor` đọc header `X-User-Id`. Giai đoạn này chưa có auth nên tin header; khi bật auth thì **chỉ sửa đúng class đó**.

## Thread pool

- `spring.threads.virtual.enabled: true` lo phần request. **Không tạo executor cho tầng web.**
- Chỉ tạo executor có giới hạn ở nơi cần backpressure — outbox publisher và fan-out: bounded queue + `CallerRunsPolicy`, tên thread rõ ràng (`outbox-`, `fanout-`). Queue vô hạn là cách biến một sự cố chậm thành `OutOfMemoryError`.
- Không dùng `@Async` cho việc phải chắc chắn xảy ra. Việc đó đi qua outbox.

## Logging

Cấu hình trong `src/main/resources/logback-spring.xml`. **Đó là nguồn duy nhất** — không đặt `logging.pattern.*` trong `application.yaml` nữa (hai nơi cấu hình là hai nơi phải sửa).

### Sáu nhóm trường bắt buộc

| Trường | Mục đích | Lấy từ đâu |
|---|---|---|
| Timestamp + timezone | Sự việc xảy ra lúc nào | `%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX}` — ISO-8601 có offset, **không** dùng timestamp không timezone |
| Level | `INFO` `WARN` `ERROR` `DEBUG` | `%-5level` |
| Service, thread, logger | Log đến từ đâu | `${APP_NAME}`, `%thread`, `%logger{40}` |
| Trace ID | Theo dõi một luồng qua nhiều service | MDC `traceId` — `LogContextFilter` đọc header `X-Trace-Id`, không có thì sinh mới |
| Event + ID nghiệp vụ | Hành động nào, với đối tượng nào | Phần message, dựng bằng `LogEvent.of(...)` |
| Duration, error code | Phân tích độ trễ và lỗi | `durationMs`, `errorCode` trong message; aspect tự thêm `durationMs` cho service |

### Khuôn một dòng log

```
2026-10-15T09:21:44.512+07:00 INFO  svc=chat-service thread=http-nio-8080-exec-3 \
logger=c.s.c.s.impl.MessageServiceImpl trace=9b1f3c4e req=7a21 user=u-10427 \
event=message.sent conversationId=3f0a7c92 messageId=c7d8e9f0 seq=185 durationMs=12
```

- Phần trước `event=` do pattern của logback in ra, lập trình viên không phải nghĩ tới.
- Phần từ `event=` trở đi do code quyết định, và **phải** dựng qua `LogEvent.of(event, k, v, ...)` để định dạng `key=value` nhất quán — grep được, cắt cột được, sau này đẩy sang log tập trung thì parse được.
- Thiếu giá trị thì in `-`, không in chuỗi rỗng.

### Quy tắc

- SLF4J. Không `System.out`, không `e.printStackTrace()`.
- MDC có đúng 3 khóa, do `LogContextFilter` ghi: `traceId` (xuyên service, đọc header `X-Trace-Id`, không có thì sinh mới), `requestId` (một hop), `userId`. **Không** `MDC.put` rải rác trong service — chỉ filter được ghi.
- Đặt tên filter/bean của ta tránh trùng tên bean Spring tự đăng ký (`requestContextFilter`, `corsFilter`...). `@Component` lấy tên bean theo tên class; trùng là app không khởi động được. Khi thêm filter mới, kiểm tên trước.
- Filter phải `remove` cả 3 khóa trong `finally`. Virtual thread tái sử dụng, nên MDC rò rỉ sẽ gán log của request này cho user của request trước, và log vẫn "trông đúng" nên rất khó phát hiện.
- Khi publish Kafka hoặc gọi service khác: truyền `traceId` kèm theo (header Kafka `X-Trace-Id`, hoặc field `traceId` trong event). Không truyền thì trace đứt đúng chỗ cần nhất.
- `LoggingAspect` log `durationMs` cho mọi method service ở DEBUG, WARN khi vượt 500ms. **Không** viết log vào/ra bằng tay trong service.
- Lỗi: `GlobalExceptionHandler` log một lần kèm `errorCode`. Các tầng dưới không log lại cùng lỗi đó.
- `WARN` cho việc bất thường đã xử lý được (outbox retry, chậm quá ngưỡng). `ERROR` chỉ khi cần người xem.
- **Không log `message.body`** ở bất kỳ mức nào, kể cả DEBUG. Chỉ log `messageId`, `conversationId`, `seq`.
- Appender: console cho local, file có rolling theo ngày + giới hạn dung lượng cho môi trường chạy thật. Chưa dùng JSON encoder vì phải thêm dependency; `key=value` đã đủ parse. Đổi sang JSON khi có log tập trung thật.

## Aspect

- Đúng **một** aspect: `LoggingAspect`. Pointcut giới hạn trong `..service..`.
- Không dùng aspect cho nghiệp vụ, cho transaction hay cho phân quyền. Aspect làm nghiệp vụ là code vô hình, debug rất khổ.
- Muốn thêm aspect thứ hai thì phải thêm một mục vào file này kèm lý do.

## Xử lý lỗi

- Một `ApiException` + enum `ErrorCode`. Không tạo một class exception cho mỗi lỗi.
- `GlobalExceptionHandler` chuyển sang `ProblemDetail`.
- Quy ước mã (từ specs): `404` khi không phải thành viên **hoặc** không tồn tại (không phân biệt, để không tiết lộ), `403` khi là thành viên nhưng thiếu vai trò, `409` xung đột nghiệp vụ, `422` body sai.
- Không bắt `Exception` rồi trả 200. Không `catch` rồi `return null`.
