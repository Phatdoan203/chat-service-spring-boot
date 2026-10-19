# ADR-0005: Cấp seq per-conversation bằng UPDATE RETURNING

**Ngày**: 2026-10-15
**Trạng thái**: proposed
**Người quyết định**: chủ dự án (chờ chốt)

## Bối cảnh

Client phải biết thứ tự tin, phải phát hiện được mình thiếu tin sau khi mất kết nối, và server phải tính số tin chưa đọc mà không quét bảng. Ba nhu cầu này đều dẫn về một câu hỏi: lấy gì làm thứ tự cho tin?

Thứ tự đến của frame WebSocket không đáng tin (xem [../03-kafka.md](../03-kafka.md)), nên thứ tự phải do DB cấp.

## Quyết định

Mỗi hội thoại có một bộ đếm riêng: `conversation.last_seq`. Khi gửi tin, trong transaction chạy

```sql
UPDATE conversation SET last_seq = last_seq + 1 WHERE id = ? RETURNING last_seq
```

và dùng giá trị trả về làm `message.seq`. Thêm `UNIQUE (conversation_id, seq)` làm lưới an toàn.

## Phương án đã xem xét

### Phương án 1: Dùng created_at làm thứ tự

- **Lợi**: không thêm cột, không khóa dòng.
- **Hại**: hai tin trong cùng millisecond không phân biệt được; client **không** biết mình thiếu tin; đồng hồ lệch là sai thứ tự.
- **Vì sao loại**: không trả lời được câu "tôi có thiếu tin nào không", mà đó là nhu cầu bắt buộc.

### Phương án 2: bigserial toàn cục cho bảng message

- **Lợi**: đơn giản nhất, không khóa dòng, không tuần tự hóa.
- **Hại**: số nhảy giữa các hội thoại (185 rồi 912) nên client không suy ra được là thiếu tin hay chỉ là hội thoại khác. `afterSeq` mất ý nghĩa và phải thay bằng con trỏ khác.
- **Vì sao loại**: mất đúng tính chất "liên tục không hở" mà cả thiết kế đang dựa vào.

### Phương án 3: SELECT max(seq) + 1 rồi INSERT

- **Lợi**: không cần cột `last_seq`.
- **Hại**: race kinh điển — hai transaction cùng đọc `max` rồi cùng ghi một số.
- **Vì sao loại**: sai về tính đúng đắn. `UNIQUE (conversation_id, seq)` sẽ chặn, nhưng khi đó người dùng nhận lỗi ngẫu nhiên đúng lúc tải cao.

## Hệ quả

### Tích cực

- `seq` liên tục không hở trong một hội thoại, nên client **phát hiện được** mình thiếu tin (nhận 187 khi đang có 185) và tự gọi `afterSeq`.
- Unread = `conversation.last_seq - member.last_read_seq`: một phép trừ, không `COUNT(*)`, nhanh như nhau với hội thoại 10 tin hay 10 triệu tin.
- Thứ tự lưu DB là nguồn sự thật duy nhất, không phụ thuộc đồng hồ hay đường truyền.
- Phân trang keyset theo `seq` dùng index `(conversation_id, seq DESC)` cho mọi truy vấn lịch sử.
- Cắt lịch sử cho thành viên mới chỉ là `seq > join_seq`, không cần cơ chế riêng.

### Tiêu cực

- Câu `UPDATE` khóa dòng `conversation` tới hết transaction, nên **mọi tin trong một hội thoại được ghi tuần tự**. Một hội thoại không vượt được khoảng vài trăm tin/giây.
- Dòng `conversation` thành điểm nóng ghi với hội thoại bận.
- Phải nhớ cấp `seq` trong **cùng** transaction với `INSERT message`, nếu không là có lỗ trong dãy số.

### Rủi ro

- Transaction gửi tin giữ khóa lâu (ví dụ gọi API ngoài khi đang mở transaction) sẽ làm cả hội thoại đứng. Giảm nhẹ: trong transaction **không** gọi mạng, không gọi service khác — quy tắc này ghi vào `CLAUDE.md`.
- Có người viết code cấp `seq` theo cách khác. Giảm nhẹ: `UNIQUE (conversation_id, seq)` làm DB chặn ngay thay vì âm thầm sinh dữ liệu sai, và test đồng thời ở bước 3 của kế hoạch sẽ bắt được.
