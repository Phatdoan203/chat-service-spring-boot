# Từ điển dữ liệu — chat-service

Mô tả từng cột, kiểu và **tác dụng** của nó. Bám sát `src/main/resources/db/migration/V1__init_chat.sql` (schema đang chạy thật). Lý do thiết kế sâu hơn xem `.claude/specs/04-du-lieu-va-api.md` và các ADR.

Quy mô: chat 1-1 (nhắn tự do) + chat nhóm. Chưa có xác thực (userId tin từ header). Không có bảng `user` — `userId` chỉ là chuỗi, danh tính thuộc hệ thống khác.

4 bảng:

```
conversation ──┬── conversation_member   (ai ở trong hội thoại nào)
               ├── message               (nội dung tin)
               └── outbox_event          (sự kiện chờ phát ra Kafka)
```

---

## Bảng `conversation` — một cuộc hội thoại (DM hoặc nhóm)

| Cột | Kiểu | Null? | Tác dụng |
|---|---|---|---|
| `id` | uuid | không (PK) | Khóa chính. UUID ngẫu nhiên (`gen_random_uuid()`) để **không ai đoán được** id hội thoại của người khác. |
| `type` | varchar(8) | không | `DM` (1-1) hoặc `GROUP` (nhóm). Quyết định cách đối xử của gần như mọi nghiệp vụ. |
| `dm_key` | varchar(140) | có | **Chỉ dùng cho DM.** Chuỗi `userA:userB` (nhỏ trước, lớn sau) — hai người bất kể ai mở trước đều ra cùng chuỗi. Có `UNIQUE` nên **chống tạo trùng hội thoại 1-1**. Nhóm thì cột này rỗng. |
| `title` | varchar(200) | có | Tên nhóm. DM không dùng (tên hiển thị suy từ người kia). |
| `created_by` | varchar(64) | không | userId người tạo. Chỉ là giá trị, không khóa ngoại sang đâu. |
| `last_seq` | bigint | không (mặc định 0) | **Bộ đếm số thứ tự tin của hội thoại này.** Mỗi lần gửi tin, cột này `+1` và giá trị đó thành `seq` của tin. Không bao giờ giảm. Đây là trái tim của thiết kế — xem mục "seq" cuối file. |
| `last_message_id` | uuid | có | Id tin cuối cùng. Cập nhật trong cùng transaction gửi tin, để **danh sách hội thoại lấy tin cuối chỉ bằng một truy vấn**, không phải đi tìm. Cố ý không đặt khóa ngoại sang `message` (tránh vòng khóa ngoại hai chiều). |
| `last_message_at` | timestamptz | có | Thời điểm tin cuối. Dùng để **sắp xếp danh sách hội thoại** (mới nhất lên trên) và làm con trỏ phân trang. |
| `created_at` | timestamptz | không | Lúc tạo hội thoại. |

**Ràng buộc & index:**
- `type IN ('DM','GROUP')` — chặn giá trị lạ.
- `(type='DM') = (dm_key IS NOT NULL)` — DM **bắt buộc** có `dm_key`, GROUP **bắt buộc** không có. Không cho trạng thái nửa vời.
- `last_seq >= 0`.
- `UNIQUE (dm_key)` — lưới chống trùng DM ở tầng DB, không cần khóa ở Java.

---

## Bảng `conversation_member` — ai ở trong hội thoại, đọc tới đâu

Mỗi dòng = một người trong một hội thoại.

| Cột | Kiểu | Null? | Tác dụng |
|---|---|---|---|
| `conversation_id` | uuid | không (PK) | Thuộc hội thoại nào. Khóa ngoại sang `conversation`. |
| `user_id` | varchar(64) | không (PK) | Là ai. Cùng `conversation_id` tạo **khóa chính tổ hợp** → một người không thể vào cùng hội thoại hai lần. |
| `role` | varchar(8) | không (mặc định MEMBER) | `OWNER` (người tạo nhóm, quản lý thành viên) hoặc `MEMBER`. |
| `status` | varchar(8) | không (mặc định ACTIVE) | `ACTIVE` đang ở trong / `LEFT` tự rời / `REMOVED` bị xóa. Xóa thành viên là đổi status, **không** xóa dòng — giữ được lịch sử ai từng ở trong nhóm và mốc `join_seq`. |
| `join_seq` | bigint | không | `conversation.last_seq` tại lúc người này vào. **Cắt lịch sử**: mọi truy vấn tin chỉ trả `seq > join_seq`, nên người mới không đọc được tin trước khi họ tham gia. |
| `last_delivered_seq` | bigint | không (mặc định 0) | Con trỏ **"đã nhận" tới seq nào**. Chỉ tăng. Dùng suy ra trạng thái DELIVERED (2 dấu tick). |
| `last_read_seq` | bigint | không (mặc định 0) | Con trỏ **"đã đọc" tới seq nào**. Chỉ tăng. Dùng tính **số tin chưa đọc** = `conversation.last_seq - last_read_seq` (một phép trừ, không cần đếm). |
| `joined_at` | timestamptz | không | Lúc tham gia. |
| `left_at` | timestamptz | có | Lúc rời/bị xóa. Rỗng khi đang ACTIVE. |

**Ràng buộc & index:**
- `role IN ('OWNER','MEMBER')`, `status IN ('ACTIVE','LEFT','REMOVED')`.
- `(status='ACTIVE') = (left_at IS NULL)` — còn ở trong thì chưa có `left_at`, và ngược lại.
- `last_read_seq <= last_delivered_seq` — **đã đọc thì tất nhiên đã nhận**. Vì vậy khi cập nhật "đã đọc" phải kéo luôn `last_delivered_seq` lên; quên là DB báo lỗi (chốt an toàn cố ý).
- `join_seq >= 0 AND last_delivered_seq >= 0`.
- `INDEX (user_id, status)` — phục vụ câu hỏi "các hội thoại ACTIVE của tôi".

---

## Bảng `message` — nội dung từng tin

| Cột | Kiểu | Null? | Tác dụng |
|---|---|---|---|
| `id` | uuid | không (PK) | Khóa chính. UUID ngẫu nhiên; client dùng để **khử trùng** khi nhận cùng tin hai lần. |
| `conversation_id` | uuid | không | Tin thuộc hội thoại nào. Khóa ngoại sang `conversation`. |
| `seq` | bigint | không | **Số thứ tự trong hội thoại**, liên tục không hở (1,2,3...). Client sắp tin theo cột này, và nhờ nó liên tục nên **phát hiện được mình thiếu tin** (nhận 187 khi đang có 185 → gọi lấy bù). |
| `sender_id` | varchar(64) | không | userId người gửi. |
| `type` | varchar(16) | không (mặc định TEXT) | `TEXT` / `IMAGE` / `FILE`. |
| `body` | text | có | Nội dung chữ. Rỗng với tin chỉ có file. |
| `attachment_id` | varchar(64) | có | Id file từ file-service (chat-service **không** giữ byte file). Chỉ dùng cho IMAGE/FILE. |
| `client_message_id` | varchar(64) | không | Id do **client** sinh, giữ nguyên qua mọi lần gửi lại. Là chìa khóa **chống gửi trùng khi mất mạng/retry**. |
| `created_at` | timestamptz | không | Thời điểm server lưu tin (không tin đồng hồ client). |
| `deleted_at` | timestamptz | có | Mốc thu hồi mềm (tính năng tương lai). Hiện luôn rỗng. |

**Ràng buộc & index:**
- `seq > 0`; `type IN ('TEXT','IMAGE','FILE')`.
- TEXT phải có `body` không rỗng; IMAGE/FILE phải có `attachment_id`.
- `body` tối đa 4000 ký tự — chặn người dán cả file log làm vỡ danh sách hội thoại.
- `UNIQUE (conversation_id, seq)` — **lưới an toàn**: nếu logic cấp `seq` sai thì DB chặn ngay, không âm thầm ghi dữ liệu sai. Cũng phục vụ mọi truy vấn lịch sử (Postgres quét index ngược được).
- `UNIQUE (conversation_id, sender_id, client_message_id)` — **chống lưu trùng**: gửi lại cùng `client_message_id` sẽ đụng ràng buộc này, app bắt lỗi và trả về tin đã có thay vì tạo tin thứ hai.

---

## Bảng `outbox_event` — sự kiện chờ phát ra Kafka

Mỗi lần gửi tin, trong **cùng transaction** ghi một dòng ở đây. Một job (Bước 5, chưa làm) sẽ đọc và đẩy sang Kafka. Mục đích: nếu commit DB xong mà chưa kịp phát Kafka thì dòng vẫn còn đây để phát lại — **không mất sự kiện**.

| Cột | Kiểu | Null? | Tác dụng |
|---|---|---|---|
| `id` | bigserial | không (PK) | Số **tăng dần** (không phải UUID) vì ở đây cần đúng **thứ tự phát**. |
| `aggregate_type` | varchar(32) | không (mặc định CONVERSATION) | Loại đối tượng sinh sự kiện. |
| `aggregate_id` | uuid | không | Id đối tượng (`conversationId`) — dùng làm **key Kafka** để tin cùng hội thoại vào cùng partition, giữ thứ tự. |
| `event_type` | varchar(40) | không | `MessageCreated` / `ReadUpdated` / ... |
| `payload` | jsonb | không | Toàn bộ nội dung sự kiện, sẵn sàng publish. |
| `created_at` | timestamptz | không | Lúc ghi outbox. |
| `published_at` | timestamptz | có | Rỗng = **chưa phát**. Có giá trị = đã đẩy sang Kafka. |
| `attempts` | int | không (mặc định 0) | Số lần thử phát — phát hiện dòng publish hoài không được. |

**Index:** `(id) WHERE published_at IS NULL` — **partial index** chỉ chứa dòng chưa phát, nên luôn nhỏ dù bảng có hàng triệu dòng; job quét outbox nhờ đó không chậm dần theo thời gian.

---

## Hai điểm thiết kế quan trọng nhất

### 1. `seq` cấp bằng `conversation.last_seq`, không phải timestamp hay id toàn cục
Khi gửi tin: `UPDATE conversation SET last_seq = last_seq + 1 ... RETURNING last_seq` trong transaction. Câu này khóa dòng `conversation` nên hai người gửi cùng lúc bị **xếp hàng**, mỗi người nhận một số khác nhau → `seq` **liên tục, không hở, không trùng** trong một hội thoại. Nhờ đó: client sắp tin đúng thứ tự, biết mình thiếu tin, và số tin chưa đọc chỉ là một phép trừ. (ADR-0005)

### 2. Trạng thái tin (SENT / DELIVERED / READ) **không** lưu trên `message`
Bảng `message` cố ý **không có cột status**. Trạng thái suy ra từ hai con trỏ trên `conversation_member`:

| Trạng thái | Nghĩa | Suy ra từ |
|---|---|---|
| SENT | Server đã lưu, đã có `seq` | dòng `message` tồn tại |
| DELIVERED | Người nhận đã nhận tới seq X | `last_delivered_seq` của thành viên |
| READ | Người nhận đã đọc tới seq X | `last_read_seq` của thành viên |

Lý do: lưu status trên từng tin sẽ bắt cập nhật rất nhiều dòng `message` cho mỗi người nhận; hai con trỏ cho cùng thông tin với chi phí gần như bằng 0 khi gửi. Giới hạn: chỉ biết "đã nhận tới seq nào", không biết "thiết bị nào nhận" — khi cần chi tiết theo thiết bị thì thêm bảng riêng, không sửa bảng này.

### Không có bảng `user`
`userId` khắp nơi chỉ là `varchar(64)`, không khóa ngoại. Danh tính (tên, ảnh, vai trò gốc) thuộc hệ thống khác; chat-service chỉ lưu id. Đây là ranh giới cố ý để service độc lập.
