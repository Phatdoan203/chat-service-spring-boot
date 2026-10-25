# ADR-0006: Có bảng người dùng; danh tính là uuid có FK

**Trạng thái**: accepted

## Bối cảnh

Ban đầu service không có bảng user: `user_id`, `sender_id`, `created_by` là varchar thô, tin header `X-User-Id`. Không có gì ngăn id rác hoặc id viết khác nhau cho cùng một người đi vào dữ liệu chat.

## Quyết định

Đảo bất biến "không có bảng user". Thêm bảng `app_user` (V2). Ba cột danh tính đổi sang `uuid` và có FK tới `app_user(id)`. Dữ liệu cũ (đều là chuỗi UUID hợp lệ) được seed vào `app_user` trước khi thêm FK. `dm_key` giữ nguyên, `DmKeys` vẫn sắp theo `toString()` của UUID. `X-User-Id` sai định dạng UUID trả 400.

## Hệ quả

- Phải tạo user (`POST /api/v1/users`) trước khi dùng id đó để mở hội thoại hay gửi tin; id không tồn tại vi phạm FK.
- Nhận diện vẫn tin header (chưa có auth); chỉ khác là id phải là UUID có thật trong `app_user`.
- Migration V2 sẽ lỗi nếu dữ liệu cũ có id không phải UUID; dữ liệu đã được xác nhận hợp lệ.
- Không có `displayName` trong response chat; thêm sau nếu cần.
