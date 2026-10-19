# Coding standards

Nguyên tắc chi phối: **code ngắn nhất mà giải quyết đủ việc**. Không thêm option, config hay lớp trừu tượng mà chưa ai cần. Xóa tốt hơn thêm.

## Java

- Java 21. `record` cho DTO, `switch` biểu thức, `var` khi kiểu đã rõ ở bên phải.
- Constructor injection, **không** `@Autowired` trên field. Nhiều dependency thì `@RequiredArgsConstructor` + field `final`.
- `Optional` chỉ làm **giá trị trả về** của repository. Không truyền `Optional` làm tham số, không để `Optional` làm field.
- Không trả `null` từ method public. Danh sách rỗng là `List.of()`.
- Không dùng `Object` hay `Map<String,Object>` cho dữ liệu có hình dạng biết trước.
- Validate ở biên: `@Valid` + `@NotBlank` / `@Size` trên record request. Trong service chỉ kiểm nghiệp vụ.

## Service: interface + impl

- Interface trong `service/`, implementation trong `service/impl/` với hậu tố `Impl`.
- Interface **chỉ khai báo method mà bên ngoài gọi**. Method chỉ dùng nội bộ thì để `private` trong impl, không đưa lên interface.
- `@Transactional` đặt trên method public của **impl**, không đặt trên interface (proxy hoạt động nhưng đọc dễ nhầm), không đặt trên class.
- Mặc định `@Transactional(readOnly = true)` cho method đọc.
- **Không gọi mạng, không gọi service ngoài, không publish Kafka trong transaction** — transaction gửi tin giữ khóa dòng `conversation` (ADR-0005). Việc sau commit thì ghi `outbox_event`.
- Một method = một nghiệp vụ hoàn chỉnh, đọc hết trong một màn hình. Không `if` lồng quá 2 tầng, thoát sớm bằng guard clause.

## DTO

- `dto/request/` cho dữ liệu vào, `dto/response/` cho dữ liệu ra. Không dùng một record cho cả hai chiều — chúng sẽ tiến hóa khác nhau.
- **Không trả entity ra controller.** Service trả record trong `dto/response/`.
- Truy vấn nhiều cột đặc thù (danh sách hội thoại kèm unread) dùng **interface projection** tên `*View`, rồi map sang response trong impl.
- Không MapStruct. Map bằng một static factory trong record response: `MessageResponse.from(message)`.

## Util

`common/util/` được phép, nhưng để nó không thành bãi rác:

- Chỉ **hàm thuần, `static`, không state**. Class `final`, constructor `private`.
- Mỗi class một chủ đề và tên nói đúng chủ đề đó: `DmKeys`, `LogEvent`.
- **Không** `CommonUtils`, `Helper`, `Utils`, `StringUtils` của riêng mình.
- Không inject gì vào util. Cần dependency nghĩa là nó thuộc service, không thuộc util.
- Logic dùng đúng một chỗ thì để nguyên chỗ đó; chuyển vào util khi có **chỗ thứ hai** cần.

## Những gì vẫn không viết

- **Không `BaseEntity`, `BaseService`, `BaseController`.** Kế thừa để chia sẻ 3 field làm mọi class dính vào nhau.
- **Không generic `CrudService<T, ID>`.** Mỗi service có 3–5 method cụ thể.
- **Không builder** cho record hoặc class dưới 5 field — gọi constructor.
- **Không getter/setter cho field không ai dùng.**
- **Không thêm dependency** cho vài dòng code. Cần thêm thì nói trước lý do.
- **Không cache** cho tới khi có số đo chứng minh là cần.

## Logging

Mọi log nghiệp vụ phải đủ trường theo `architecture.md`. Trong code:

- SLF4J. Không `System.out`, không `e.printStackTrace()`.
- Dùng `LogEvent` để dựng phần message, **không tự ghép chuỗi**:
  `log.info(LogEvent.of("message.sent", "conversationId", id, "seq", seq, "durationMs", ms))`
- Tên event dạng `<danh_tu>.<hanh_dong>`: `message.sent`, `conversation.created`, `member.removed`, `outbox.published`. Lỗi thì thêm hậu tố `.failed`.
- Log một lần tại biên (controller / consumer / exception handler). Không log lại cùng một lỗi ở mỗi tầng.
- `WARN` cho việc bất thường đã xử lý được (outbox retry, chậm quá ngưỡng). `ERROR` chỉ khi cần người xem.
- **Không log `message.body`** ở bất kỳ mức nào. Chỉ log `messageId`, `conversationId`, `seq`.
- Không truyền tham số vào log của aspect — tham số có thể chứa nội dung tin.

## Comment

- Chỉ giải thích **vì sao**, một dòng, khi code không nói được.
- Shortcut có giới hạn đã biết: `// shortcut: <giới hạn>, <khi nào nâng>`.
- Không javadoc cho getter, setter, constructor hiển nhiên. Không để code đã comment lại trong file.

## Repository

- Ưu tiên method name derivation (`findByConversationIdAndUserId`). Tên dài quá 6 từ thì chuyển `@Query`.
- Câu cấp `seq`, cập nhật con trỏ đọc và danh sách hội thoại viết `@Modifying @Query` / `@Query` native — đó là nơi tính đúng đắn nằm.
- Không `findAll()` không điều kiện trên `message`.

## Test

**KHÔNG viết unit test trong project.** Không tạo file nào trong `src/test/`, không thêm dependency test (Testcontainers, Mockito, AssertJ...).

Hệ quả phải bù lại bằng cách khác — tính đúng đắn vẫn phải được chứng minh, chỉ là không bằng JUnit:

- **Đẩy ràng buộc xuống DB.** Mỗi quy tắc nào DB giữ được thì phải để DB giữ: `UNIQUE (conversation_id, seq)`, `UNIQUE (conversation_id, sender_id, client_message_id)`, `UNIQUE (dm_key)`, `CHECK (last_read_seq <= last_delivered_seq)`. Ràng buộc trong migration thay vai trò của test: logic sai thì DB chặn ngay thay vì âm thầm ghi dữ liệu sai.
- **Không mô phỏng điều DB làm được.** Cấp `seq` bằng `UPDATE ... RETURNING`, chống trùng DM bằng `ON CONFLICT`. Không `SELECT` rồi `INSERT`, không khóa ở tầng Java — không có test thì những race đó sẽ không bao giờ bị phát hiện trước production.
- **Kiểm chứng bằng tay qua API thật.** Cách kiểm sống trong **springdoc**, không trong file markdown riêng: `OpenApiConfig` cho phần chung, `@Operation(description = ...)` cho từng endpoint. Swagger UI render markdown nên khối ```` ``` ```` curl hiện đúng. Một nguồn duy nhất, nằm cạnh code, không mục theo thời gian.
- Mỗi endpoint mới phải kèm trong `@Operation` của chính nó: câu lệnh gọi được và kết quả mong đợi (gọi hai lần cùng `clientMessageId`, gọi song song, đánh dấu đã đọc lùi...).
- Lỗi do thiếu test sẽ xuất hiện muộn hơn và trong môi trường thật. Đây là lựa chọn đã biết, không phải sơ suất.

## Git

- Repo chưa `git init`. Khi init, `.gitignore` phải có `target/`, `*.log`, `logs/`, `application-local.yaml`, `.env`.
- **Không commit chuỗi kết nối hay mật khẩu Supabase.**
- Commit nhỏ, một việc một commit, message dạng `feat: gui tin voi idempotency`.
