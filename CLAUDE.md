# chat-service

Spring Boot 4.1.1 on Java 21. Maven wrapper: `./mvnw test`, `./mvnw spring-boot:run`.
Stack: Web MVC + WebSocket, JPA + Flyway on PostgreSQL, Kafka, springdoc OpenAPI.
Source root `src/main/java/com/side_project/chat_service`, config in `src/main/resources/application.yaml`.

## Rules

Trước khi viết code: `.claude/rules/project-structure.md`, `architecture.md`, `coding-standards.md`.
Rules thắng thói quen mặc định. Hiện thực code thì dùng agent `coder` (nó tự đọc 3 file này).

DB là Supabase, kết nối qua 3 biến môi trường `SUPABASE_DB_URL` / `_USER` / `_PASSWORD`.
Không hardcode mật khẩu vào `application.yaml`.

## Specs

Thiết kế nằm ở `.claude/specs/` — đọc trước khi viết code nghiệp vụ.
`README.md` (giả định + câu hỏi mở), `01` nghiệp vụ, `02` kiến trúc, `03` Kafka, `04` dữ liệu/API,
`05` lỗi, `06` tải + kế hoạch, `adr/` các quyết định. Đề bài gốc: `de_bai.md`.

Hai bất biến mà các ADR dựa vào:

- **Không gọi mạng hay service khác trong transaction gửi tin** — nó giữ khóa dòng `conversation`
  và sẽ làm cả hội thoại đứng (ADR-0005).
- **Không viết code fan-out giả định chỉ có một instance** — ví dụ đếm tổng số session để báo
  presence toàn hệ thống (ADR-0004).

## Advisor

Consult the `advisor` subagent at three points, and act on its verdict before continuing:

1. **Before locking a plan** — once the approach is settled but before the first edit.
2. **After the same error twice** — on the second identical failure, stop fixing and ask.
3. **Before calling a task done** — before reporting completion, with what was changed and what was verified.
