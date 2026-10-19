# 01 — Phân tích nghiệp vụ

Mọi mục dưới đây là **đề xuất của tôi**, chưa phải yêu cầu đã chốt. Chỗ nào là giả định tôi ghi `(GIẢ ĐỊNH)`.

## Vai trò

| Vai trò | Viết tắt | Quyền nghiệp vụ liên quan tới chat |
|---------|----------|------------------------------------|
| Cộng tác viên | CTV | Nhắn trong hội thoại mình là thành viên. Không tạo nhóm. |
| Trưởng nhóm | TN | Như CTV, cộng thêm: tạo nhóm, thêm/xóa thành viên nhóm mình tạo. |
| Nhân viên hỗ trợ | SUP | Như TN. Có thể mở hội thoại 1–1 với bất kỳ CTV nào. |

Gateway đã xác thực và đã biết vai trò. chat-service **không** tự quyết định ai là TN, nó chỉ đọc vai trò từ request và kiểm tra **quyền trong hội thoại cụ thể** (bảng quyền ở cuối file).

---

## Use case bắt buộc cho MVP

| # | Use case | Ghi chú thiết kế |
|---|----------|------------------|
| U1 | Mở hội thoại 1–1 với một người | Phải **idempotent**: gọi 2 lần ra cùng một hội thoại, không tạo trùng |
| U2 | Tạo nhóm với danh sách thành viên ban đầu | Người tạo là OWNER |
| U3 | Thêm / xóa thành viên nhóm | Chỉ OWNER hoặc SUP |
| U4 | Rời nhóm | OWNER rời thì phải chuyển quyền hoặc bị chặn — xem Q3 |
| U5 | Gửi tin text vào hội thoại | Đồng bộ, trả về `messageId` + `seq` |
| U6 | Nhận tin realtime | WebSocket, server đẩy xuống |
| U7 | Danh sách hội thoại + tin cuối + số tin chưa đọc | Một request, không N+1 |
| U8 | Xem lịch sử tin, phân trang lùi | Keyset theo `seq`, không OFFSET |
| U9 | Đánh dấu đã đọc tới tin X | Con trỏ đọc, chỉ tăng |
| U10 | Đồng bộ "đã đọc" sang thiết bị/tab khác của **chính tôi** | Không có thì đọc trên điện thoại mà web vẫn báo chưa đọc |
| U11 | Gửi lại tin sau khi mất response, không bị nhân đôi | `clientMessageId` do client sinh |
| U12 | Lấy bù tin đã bỏ lỡ sau khi mất kết nối | `GET ...?afterSeq=` — **không** dựa vào Kafka để phát lại |

U11 và U12 thường bị bỏ ở bản đầu rồi thành bug không sửa nổi. Chúng ở MVP vì chúng **quyết định schema** (`clientMessageId`, `seq`), không phải vì chúng là tính năng người dùng nhìn thấy.

## Có thể bổ sung sau

| Use case | Trì hoãn được vì | Khi thêm thì đụng vào đâu |
|----------|------------------|---------------------------|
| Gửi ảnh / tài liệu | Text đã chứng minh được toàn bộ đường đi của tin | Thêm cột `attachment_id`, hợp đồng với file-service |
| Push notification khi offline | Không có nó vẫn chat được, chỉ là biết muộn | Consumer mới trên topic có sẵn, không đổi schema |
| Thu hồi / xóa tin | Nghiệp vụ chưa chốt (xóa cho mình hay cho mọi người) | `deleted_at` + event `MESSAGE_DELETED` |
| Trạng thái "đã nhận" (delivered) | MVP chỉ cần "server đã lưu" và "đã đọc" | Bảng per-device — phần nặng nhất nhóm này |
| Đang gõ (typing) / online (presence) | Thuần UI, không cần lưu | Chỉ qua WebSocket, không vào DB, không vào Kafka |
| Tìm kiếm tin | Dùng ít ở quy mô này | Postgres full-text trước, chưa cần Elasticsearch |
| Ghim / tắt thông báo hội thoại | Tiện lợi | Cột trên `conversation_member` |

## Chưa cần ở quy mô này

Nêu ra để chốt là **không làm**, tránh bàn lại:

- Tách `realtime-service` riêng — đề bài đã nói không mặc định, và một instance thì vô nghĩa.
- Nhiều instance + định tuyến session. Hướng đi có trong [03](03-kafka.md), chưa triển khai.
- Chia bảng `message` theo thời gian (partition). Ở 5.000 tài khoản phải nhiều năm mới tới mức cần. Làm sớm là khóa luôn cách đánh index.
- Đổi sang Cassandra/ScyllaDB cho message store.
- "Ai đã đọc" chi tiết trong nhóm, reaction, trả lời theo luồng (thread), sửa tin.
- Mã hóa đầu-cuối (E2EE): phá vỡ tìm kiếm, kiểm duyệt và push — là quyết định sản phẩm, không phải hạng mục kỹ thuật thêm sau.

---

## 11 câu hỏi của đề bài — trả lời kèm giả định

**1. Chat 1–1 giữa những vai trò nào?** — **ĐÃ CHỐT 2026-10-15: nhắn tự do.**
Bất kỳ ai cũng mở được hội thoại 1–1 với bất kỳ ai, không kiểm quan hệ nghiệp vụ. Xác thực và phân quyền làm sau; giai đoạn này coi như mọi người full quyền.
Hệ quả schema: `conversation.dm_key` vẫn cần (chống tạo trùng hội thoại 1–1), nhưng **không** cần gọi sang service tổ chức/nhân sự để kiểm quan hệ — bớt một dependency.
Rủi ro để lại: không có hàng rào chống spam giữa người lạ. Khi bật phân quyền thật thì đây là chỗ phải quay lại, và nó là thay đổi ở tầng kiểm tra lúc **tạo hội thoại**, không phải thay đổi schema.

**2. Chat nhóm: ai quản lý?** (GIẢ ĐỊNH)
TN tạo nhóm và là OWNER; OWNER và SUP thêm/xóa thành viên; CTV chỉ tham gia và rời.

**3. Thành viên mới có xem lịch sử trước khi tham gia?** (GIẢ ĐỊNH: **không**)
Thực hiện bằng `conversation_member.join_seq` = `conversation.last_seq` tại thời điểm thêm vào. Mọi truy vấn lịch sử thêm điều kiện `seq > join_seq`. Rẻ, không cần kiểm tra gì thêm, và tự nhiên cho nhóm công việc — người mới không đọc được thương lượng giá cũ.

**4. Người rời / bị xóa còn xem lịch sử?** (GIẢ ĐỊNH: **không** — giả định yếu)
`status` chuyển `LEFT` / `REMOVED` thì mất quyền đọc và gửi ngay, API trả 404.
Phương án mềm hơn: giữ quyền đọc tới `left_seq`, chặn gửi. Chi phí: mọi truy vấn lịch sử phải kẹp hai phía `join_seq < seq <= left_seq`. Không khó, nhưng **phải quyết trước khi viết truy vấn** vì nó nằm trong mọi câu đọc tin.

**5. Text, ảnh, tài liệu và quyền truy cập file.** (GIẢ ĐỊNH)
MVP: chỉ text. Khi thêm file: client upload lên **file-service** trước, nhận `attachmentId`, rồi gửi tin kèm id đó. chat-service **không** nhận byte file, chỉ lưu id và kiểm tra người gửi chính là người đã upload. Tải xuống: file-service cấp URL có thời hạn ngắn.
Rủi ro còn lại: ai có URL trong thời hạn đó thì tải được, kể cả người ngoài hội thoại — chấp nhận được với ảnh chat, **không** chấp nhận được nếu sau này gửi hợp đồng hoặc giấy tờ tùy thân. Cần chốt.

**6. Danh sách hội thoại, tin cuối, phân trang, unread.**
`conversation.last_message_id` và `last_message_at` cập nhật trong cùng transaction gửi tin, nên danh sách hội thoại là một truy vấn chứ không phải đi tìm tin cuối. Unread = `conversation.last_seq - member.last_read_seq`: phép trừ hai số, **không** `COUNT(*)`. Lịch sử phân trang keyset (`seq < ?`), không OFFSET — OFFSET chậm dần và nhảy tin khi có tin mới chèn vào.

**7. Phân biệt gửi thành công / đã nhận / đã đọc.** Chỗ hay nhập nhằng nhất:

| Trạng thái | Nghĩa chính xác | Ai biết | MVP |
|------------|-----------------|---------|-----|
| SENT | Server đã **commit** tin vào DB và đã cấp `seq` | Server trả 201 cho người gửi | **Có** |
| DELIVERED | Người nhận đã nhận được tin tới `seq` X | `conversation_member.last_delivered_seq`, do client ack | **Có** (chốt 2026-10-15) |
| READ | Người nhận **chủ động** báo đã đọc tới `seq` X | Từ `POST /read` | **Có** |

"Kafka đã nhận event" và "consumer đã xử lý" **không** phải trạng thái của tin và không bao giờ hiện lên UI. Chúng là chi tiết nội bộ.

**8. Một user nhiều thiết bị / nhiều tab.**
Mỗi tab là một WebSocket session. Một user có N session và **tất cả** đều nhận tin mới. "Đã đọc" là thuộc tính của **user trong hội thoại**, không phải của thiết bị — nên khi một thiết bị báo đã đọc, các session khác của chính user đó nhận `READ_UPDATED` để xóa badge.

**9. Offline, mất mạng, reconnect, gửi lại.**
Người nhận offline: tin vẫn lưu, fan-out không tìm thấy session thì **bỏ qua — không phải lỗi, không retry**. Khi quay lại: client gọi `GET ...?afterSeq=<seq cuối nó có>` cho từng hội thoại có `last_seq` lớn hơn con trỏ của nó. Người gửi mất response: gửi lại **cùng** `clientMessageId`, server trả về tin đã lưu kèm `seq` cũ, không tạo tin thứ hai.

**10. Sửa / xóa / thu hồi tin.** (GIẢ ĐỊNH: không có trong MVP)
Thu hồi mềm chấp nhận được: giữ nguyên `seq`, set `deleted_at`, nội dung thay bằng "tin đã thu hồi".
**Sửa** tin nguy hiểm hơn: cùng một `seq` có thể có hai nội dung ở hai thiết bị nếu thiết bị nào bỏ lỡ event sửa, nên phải thêm version cho mỗi tin. Vì vậy nếu cần thì làm **thu hồi** trước, **sửa** sau cùng hoặc không làm.

**11. Khi nào gửi push notification?** (GIẢ ĐỊNH)
**Không** chỉ dựa online/offline — "có WebSocket" không có nghĩa là người đó đang nhìn màn hình.
Quy tắc đề xuất: notification-service nhận event tin mới, chờ **30 giây**, rồi kiểm tra con trỏ đọc của người nhận; vẫn chưa đọc thì mới push. Người đang chat không bị push, người để máy trong túi thì được push, và chat-service không cần biết gì về presence. Việc này nằm hoàn toàn ở notification-service; chat-service chỉ phát event và cung cấp API đọc con trỏ đọc.

---

## Quyền nghiệp vụ chat-service phải tự kiểm

Gateway đã trả lời "anh là ai". chat-service trả lời "anh được làm gì **ở hội thoại này**":

| Hành động | Điều kiện |
|-----------|-----------|
| Đọc lịch sử hội thoại | thành viên `status=ACTIVE`, và `seq > join_seq` của chính mình |
| Gửi tin | thành viên `status=ACTIVE` |
| Đánh dấu đã đọc | thành viên `status=ACTIVE`; `seq` không vượt `conversation.last_seq` |
| Thêm / xóa thành viên | `role=OWNER` của hội thoại, hoặc vai trò SUP |
| Mở hội thoại 1–1 | hai bên thỏa quan hệ cho phép ở câu 1 |
| Xem danh sách hội thoại | luôn được, nhưng chỉ thấy hội thoại mình đang `ACTIVE` |

Nguyên tắc: **không tin bất kỳ id nào client gửi lên.** Mọi endpoint có `conversationId` phải kiểm tra membership trước khi làm gì khác, và trả **404** chứ không phải 403 khi không có quyền — 403 là tiết lộ rằng hội thoại đó tồn tại.

---

## Quyết định nghiệp vụ cần bạn chốt trước khi code

| | Câu hỏi | Mặc định tôi đang dùng |
|---|---------|------------------------|
| Q1 | CTV ↔ CTV có được nhắn trực tiếp? Quan hệ công việc kết thúc thì hội thoại cũ còn gửi được? | Không được nhắn; hội thoại cũ vẫn gửi được |
| Q2 | Người bị xóa khỏi nhóm có xem lại lịch sử tới lúc bị xóa? | Không |
| Q3 | OWNER rời nhóm thì sao? | Chặn, phải chuyển OWNER trước |
| Q4 | Có cần "đã nhận" (2 tick) trong MVP? | Không |
| Q5 | Nhóm tối đa bao nhiêu thành viên? | 50 (ảnh hưởng số lượt phát realtime, xem [06](06-tai-va-ke-hoach.md)) |
| Q6 | Giữ lịch sử chat bao lâu? Có phải xóa theo yêu cầu đối tác? | Giữ vô hạn (giả định yếu, dễ sai về pháp lý) |
