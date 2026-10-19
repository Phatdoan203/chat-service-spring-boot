# Project structure

Layer-first. Package gốc: `com.side_project.chat_service`.

```
src/main/java/com/side_project/chat_service/
├── ChatServiceApplication.java
├── config/
│   ├── AsyncConfig.java           executor co gioi han cho outbox + fan-out
│   ├── WebSocketConfig.java       STOMP /ws/chat, heartbeat, buffer limit
│   └── KafkaConfig.java           (Buoc 5, chua tao)
├── common/
│   ├── aspect/LoggingAspect.java  log vao/ra + duration cho @Service
│   ├── exception/
│   │   ├── ApiException.java      1 exception + enum, khong tao cay exception
│   │   ├── ErrorCode.java
│   │   └── GlobalExceptionHandler.java
│   ├── util/                      helper thuan, static, khong state
│   │   ├── LogEvent.java          dung chuoi event=... key=value cho log
│   │   └── DmKeys.java            sinh dm_key cho hoi thoai 1-1
│   └── web/LogContextFilter.java   traceId + requestId + userId vao MDC
├── controller/                    @RestController, mong
├── service/                       interface: hop dong nghiep vu
│   └── impl/                      implementation + @Transactional
├── repository/                    Spring Data JPA interface
├── domain/                        @Entity (Conversation, ConversationMember, ConversationMemberId)
├── enums/                         ConversationType, MemberRole, MemberStatus
├── dto/
│   ├── request/                   record nhan tu client
│   └── response/                  record tra ve client
└── messaging/                     (Buoc 5) outbox publisher, consumer, fan-out

src/main/resources/
├── application.yaml
├── logback-spring.xml             cau hinh log, xem architecture.md
└── db/migration/V1__init_chat.sql Flyway: nguon su that duy nhat cua schema

src/test/                          KHONG dung. Khong viet unit test trong project.
```

## Quy tắc về cây thư mục

- **Service có interface + impl.** `MessageService` (interface) trong `service/`, `MessageServiceImpl` trong `service/impl/`. Controller và `messaging` chỉ phụ thuộc vào interface.
- **DTO chia hai package.** `dto/request/SendMessageRequest`, `dto/response/MessageResponse`. Đều là `record`.
- **`common/util/` cho helper dùng chung.** Mỗi class một chủ đề rõ ràng và tên nói đúng việc nó làm (`DmKeys`, `LogEvent`). Xem giới hạn trong `coding-standards.md`.
- **Không tạo package riêng cho từng feature** (`conversation/`, `message/`). Service này nhỏ, layer-first đọc nhanh hơn. Đổi sang feature-first chỉ khi một layer vượt khoảng 10 file.
- File mới phải nằm đúng một trong các package trên. Cần package mới thì sửa file này trước, kèm một dòng lý do.

## Đặt tên

| Loại | Mẫu | Ví dụ |
|---|---|---|
| Controller | `<DanhTu>Controller` | `MessageController` |
| Service interface | `<DanhTu>Service` | `MessageService` |
| Service impl | `<DanhTu>ServiceImpl` | `MessageServiceImpl` |
| Repository | `<Entity>Repository` | `MessageRepository` |
| Entity | danh từ đơn | `Message`, `ConversationMember` |
| Request DTO | `<HanhDong>Request` | `SendMessageRequest` |
| Response DTO | `<DanhTu>Response` | `MessageResponse`, `ConversationResponse` |
| Projection | `<DanhTu>View`, đặt ở `dto/response/` | `ConversationListView` |
| Enum | danh từ đơn | `ConversationType`, `MemberStatus` |
| Util | danh từ số nhiều hoặc chủ đề | `DmKeys`, `LogEvent` |
