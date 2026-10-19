Bạn đóng vai Senior Java Backend Engineer có khả năng phân tích nghiệp vụ và thiết kế hệ thống. Hãy giúp tôi xác định nghiệp vụ, phân tích yêu cầu kỹ thuật và đề xuất kiến trúc backend cho chức năng chat.

Chưa viết code. Tôi muốn hiểu phương án và các đánh đổi trước khi triển khai.

## 1. Bối cảnh và ràng buộc

- Hệ thống dành cho nền tảng đối tác/cộng tác viên, có thể có các vai trò: cộng tác viên, trưởng nhóm và nhân viên hỗ trợ.
- Quy mô dự kiến 1.000–5.000 người dùng. Chưa xác định đây là tổng tài khoản hay số người online đồng thời; hãy phân tích ảnh hưởng của hai cách hiểu.
- Sử dụng Java, Spring Boot, PostgreSQL và Kafka.
- Chức năng realtime sử dụng WebSocket.
- Hệ thống theo kiến trúc microservice, nhưng không bắt buộc tách mỗi chức năng thành một service.
- Authentication và authorization chung đã được xử lý tại gateway. Không thiết kế lại hệ thống đăng nhập, cấp token hay quản lý role.
- Vẫn phải xác định những quyền nghiệp vụ mà chat-service cần kiểm tra, chẳng hạn quyền gửi và đọc tin trong một hội thoại.
- Không sử dụng Redis.
- Bản đầu ưu tiên một instance của chat-service. Không mặc định phải có realtime-service riêng hoặc chạy nhiều instance.
- Có thể tích hợp file-service và notification-service hiện có thông qua hợp đồng API/event; không thiết kế lại toàn bộ hai hệ thống này.
- Mục tiêu là phương án thực tế mà một Java Backend Developer khoảng hai năm kinh nghiệm có thể tự triển khai, kiểm thử và giải thích được.

Nếu có source code trong workspace, hãy đọc cấu trúc và cấu hình liên quan trước khi đề xuất. Phân biệt rõ điều quan sát được trong source với giả định của bạn.

## 2. Phân tích nghiệp vụ trước

Đề xuất các use case phù hợp và chia thành:
- Bắt buộc cho MVP.
- Có thể bổ sung sau.
- Chưa cần ở quy mô này.

Cần xem xét:

1. Chat 1–1 giữa những vai trò nào? Có cho phép mọi người nhắn tự do hay cần quan hệ nghiệp vụ?
2. Chat nhóm: ai tạo nhóm, thêm/xóa thành viên và quản lý nhóm?
3. Thành viên mới có xem lịch sử trước khi tham gia không?
4. Người rời hoặc bị xóa khỏi nhóm còn được xem lịch sử không?
5. Gửi text, ảnh và tài liệu; quyền truy cập file đính kèm.
6. Danh sách hội thoại, tin cuối, phân trang lịch sử và số tin chưa đọc.
7. Trạng thái gửi thành công, đã nhận và đã đọc khác nhau thế nào?
8. Một user dùng nhiều thiết bị hoặc mở nhiều tab.
9. User offline, mất mạng, reconnect hoặc gửi lại cùng một tin.
10. Sửa/xóa/thu hồi tin: có cần trong MVP không, và ảnh hưởng gì đến lịch sử?
11. Khi nào gửi push notification? Có nên chỉ dựa vào online/offline không?

Với nghiệp vụ chưa được cung cấp, ghi rõ giả định và đề xuất mặc định hợp lý. Không trình bày giả định như yêu cầu đã được xác nhận.

## 3. Đề xuất kiến trúc và luồng xử lý

Đưa ra tối đa hai phương án phù hợp với các ràng buộc trên, rồi chọn một phương án khuyến nghị.

Làm rõ:

- Ranh giới và trách nhiệm của từng service.
- Service nào sở hữu dữ liệu nào.
- REST dùng cho những thao tác nào; WebSocket dùng cho những thao tác nào.
- Có nên gửi tin bằng REST hay WebSocket? So sánh ngắn và chọn một.
- Kafka giải quyết nhu cầu cụ thể gì trong hệ thống này?
- Nên lưu DB trước rồi phát event, hay nhận lệnh qua Kafka rồi mới lưu DB?
- Có cần outbox không? Nếu có, giải thích lỗi nào nó xử lý và lỗi nào nó không xử lý.
- Khi nào API có thể trả thành công cho người gửi?
- Những bước nào xử lý đồng bộ, những bước nào bất đồng bộ?

Mô tả tuần tự ít nhất các luồng:
1. Gửi và nhận tin 1–1 khi cả hai online.
2. Gửi tin vào nhóm.
3. Người nhận offline rồi quay lại.
4. Người gửi retry vì mất response.
5. Cập nhật đã đọc và đồng bộ nhiều thiết bị.
6. Thêm/xóa thành viên trong lúc có người đang gửi tin.

## 4. Thiết kế Kafka theo đúng bài toán

Đề xuất:
- Topic, loại event và cấu trúc event.
- Message key và lý do lựa chọn.
- Partition ban đầu và cơ sở lựa chọn, không suy ra trực tiếp từ số user.
- Consumer group của từng luồng.
- Thời điểm commit offset.
- Retry, backoff và cách xử lý event lỗi.
- Idempotency tại producer, consumer và client.
- Yêu cầu giữ thứ tự trong một hội thoại.

Phân biệt rõ:
- Thứ tự lưu DB.
- Thứ tự outbox phát event.
- Thứ tự Kafka lưu trong partition.
- Thứ tự thiết bị thực tế nhận và hiển thị.

Không mặc định Kafka bảo đảm exactly-once cho toàn bộ luồng từ DB đến thiết bị.

Với một instance, ưu tiên cấu hình đơn giản. Chỉ phân tích riêng hướng mở rộng nhiều instance: nếu consumer nhận event nhưng WebSocket của người nhận nằm ở instance khác thì giải quyết thế nào khi không có Redis? Nêu đánh đổi, chưa bắt buộc triển khai.

## 5. Dữ liệu và hợp đồng giao tiếp

Đề xuất ở mức thiết kế:
- Các bảng, trường quan trọng và quan hệ.
- Khóa chính, unique constraint và index cần thiết.
- Transaction boundary, cơ chế cấp thứ tự tin và xử lý concurrent request.
- Cách chống tạo trùng hội thoại 1–1 và chống lưu trùng tin.
- Cách lưu read cursor và tính unread phù hợp với quy tắc nghiệp vụ.
- Phân trang lịch sử và API lấy bù tin sau reconnect.
- Các REST endpoint và WebSocket event chính.

Đưa một ví dụ request gửi tin và một ví dụ Kafka event. Chưa cần viết đầy đủ DDL hoặc source code.

## 6. Phân tích lỗi và giới hạn

Dùng bảng để mô tả: tình huống, ảnh hưởng tới user, dữ liệu còn ở đâu và cách phục hồi.

Ít nhất bao gồm:
- DB commit thành công nhưng chưa phát được Kafka.
- Kafka nhận event nhưng outbox chưa được đánh dấu hoàn tất.
- Consumer xử lý xong nhưng chưa commit offset.
- Consumer nhận event nhưng thiết bị đã ngắt kết nối.
- Server restart làm mất toàn bộ WebSocket.
- Kafka ngừng hoạt động.
- Event trùng hoặc đến sai thứ tự.
- Client quá chậm, buffer gửi bị đầy.
- User đoán được ID hội thoại của người khác.

Không đồng nhất “Kafka đã nhận”, “consumer đã xử lý”, “thiết bị đã nhận” và “người dùng đã đọc”.

## 7. Đánh giá tải và kế hoạch triển khai

Phân biệt:
- Tổng tài khoản.
- User online.
- Số kết nối WebSocket, có tính nhiều thiết bị.
- Tin gửi vào mỗi giây.
- Số lượt phát tới thiết bị mỗi giây.

Đưa một kịch bản tải giả định với phép tính minh họa. Không khẳng định chịu được 5.000 user khi chưa có đo đạc.

Đề xuất các bước triển khai nhỏ, theo thứ tự phụ thuộc. Mỗi bước ghi:
- Kết quả cần có.
- Cách kiểm chứng.
- Điều kiện hoàn thành.

## 8. Yêu cầu trình bày

- Viết tiếng Việt, giải thích thuật ngữ ngay khi dùng.
- Dùng sơ đồ Mermaid cho kiến trúc và luồng cần thiết.
- Đưa ra quyết định rõ ràng, không chỉ liệt kê công nghệ.
- Chỉ ra giả định yếu, yêu cầu mâu thuẫn và dữ liệu còn thiếu.
- Với mỗi thành phần bổ sung, giải thích nó giải quyết vấn đề gì và có thể trì hoãn không.
- Phân biệt yêu cầu của MVP với hướng mở rộng.
- Chỉ dùng tài liệu chính thức khi cần kiểm chứng hành vi công nghệ.
- Kết thúc bằng phương án khuyến nghị và những quyết định nghiệp vụ cần tôi chốt trước khi code.

Hãy bắt đầu bằng việc tóm tắt cách bạn hiểu bài toán và liệt kê tối đa năm câu hỏi quan trọng. Sau đó vẫn tiếp tục phân tích với giả định được ghi rõ, không dừng chỉ để chờ tôi trả lời.