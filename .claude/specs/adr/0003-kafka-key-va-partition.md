# ADR-0003: Kafka key = conversationId, 6 partition

**Ngày**: 2026-10-15
**Trạng thái**: proposed
**Người quyết định**: chủ dự án (chờ chốt)

## Bối cảnh

Kafka chỉ bảo đảm thứ tự **trong một partition**, và partition được chọn theo message key. Vì vậy key quyết định đơn vị nào được giữ thứ tự. Số partition thì gần như không đổi được về sau: tăng partition làm hàm băm key đổi chỗ, nên tin cũ và tin mới của cùng một hội thoại rơi vào hai partition khác nhau và mất thứ tự trong giai đoạn chuyển.

## Quyết định

Message key = **`conversationId`**. Số partition = **6** cho các topic nghiệp vụ, **1** cho DLT.

## Phương án đã xem xét

### Phương án 1: Key = senderId hoặc userId

- **Lợi**: phân bố đều theo người dùng; hợp nếu sau này định tuyến theo user.
- **Hại**: một tin có nhiều người nhận nên không có một userId duy nhất. Lấy `senderId` thì hai người trong cùng hội thoại gửi vào hai partition khác nhau, nên **mất thứ tự ngay trong một cuộc chat** — đúng chỗ người dùng nhìn thấy.
- **Vì sao loại**: phá vỡ đúng thứ tự duy nhất mà người dùng cảm nhận được.

### Phương án 2: Key = messageId, hoặc không key

- **Lợi**: phân bố hoàn hảo.
- **Hại**: mất hoàn toàn thứ tự.
- **Vì sao loại**: không giải quyết được gì mà mất mọi bảo đảm.

### Về số partition: 1, 3, 6 hay 12

| | |
|---|---|
| **1** | Đủ về thông lượng (~53 event/s là mức một partition xử lý thừa), nhưng khóa cứng ở một consumer: muốn thêm instance là phải repartition và mất thứ tự |
| **3** | Đủ cho 1–3 instance, nhưng hết chỗ sớm |
| **6** (chọn) | Tối đa 6 consumer song song trong một group; chia hết cho 1, 2, 3, 6 nên phân bổ luôn đều |
| **12** | Dư thừa; thêm file và metadata ở broker mà không dùng tới |

Số partition **không** suy ra từ số user. Nó suy ra từ mức song song mong muốn khi mở rộng, và từ việc đổi về sau rất đắt.

## Hệ quả

### Tích cực

- Thứ tự trong một hội thoại được Kafka giữ nguyên, khớp đúng với thứ tự `seq` trong DB.
- Có chỗ cho tới 6 instance mà không phải repartition.
- Phân bổ đều với mọi số instance thực tế.

### Tiêu cực

- Một hội thoại cực kỳ bận sẽ dồn vào một partition và không chia tải được. Với chat giữa người với người thì không xảy ra.
- 6 partition cho ~53 event/s là dư, nên có chi phí nhỏ về file và metadata ở broker.

### Rủi ro

- Nếu sau này có người tăng số partition để scale thì **thứ tự bị phá trong giai đoạn chuyển**. Giảm nhẹ: ghi rõ trong ADR này và trong tài liệu vận hành rằng số partition là quyết định một lần.
- Thứ tự Kafka chỉ đúng nếu publisher chạy **một luồng** và producer bật `enable.idempotence=true`. Hai điều kiện này phải được kiểm trong test, không chỉ nằm trong tài liệu.
