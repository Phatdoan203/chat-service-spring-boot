# Vai trò của WebSocket trong chat-service

Tóm tắt: **WebSocket chỉ là kênh một chiều để server đẩy tin realtime xuống client.**
Nó không dùng để gửi tin, và hỏng cũng không mất tin.

---

## 1. Phân vai REST vs WebSocket

```
Client  ──(REST: gửi tin, markRead, lấy lịch sử)──►  Server      ← đường GHI, nguồn sự thật
Client  ◄──(WebSocket: MESSAGE_CREATED, READ_UPDATED)── Server    ← loa thông báo realtime
```

- **Gửi tin → REST** (`POST /conversations/{id}/messages`). Client luôn gửi bằng HTTP.
- **Nhận realtime → WebSocket.** Sau khi tin đã commit, server đẩy sự kiện xuống `/user/queue/chat`.
- WebSocket **một chiều** (ADR-0001): **không** có `@MessageMapping` nào nhận tin từ client.
  Nếu đi tìm chỗ "server bắt tin client gửi qua WS" thì sẽ không có — đó là cố ý.

---

## 2. Hai đầu: ai đẩy, ai bắt

| | Phía server | Phía client |
|---|---|---|
| Vai trò | **ĐẨY** vào broker | **BẮT** (duy nhất) |
| Code | `ChatFanout.convertAndSendToUser(...)` → `SimpleBroker` | STOMP client `subscribe("/user/queue/chat")` |
| Server có "bắt" không? | **Không** — WS một chiều | — |

Server chỉ ghi vào broker; broker (cấu hình trong `WebSocketConfig`: `enableSimpleBroker("/queue")`
+ `setUserDestinationPrefix("/user")`) lo định tuyến. Người **bắt** là frontend.

Client subscribe `/user/queue/chat`, server gửi tới `/queue/chat` — Spring tự ghép `/user` + principal
thành hàng đợi riêng của từng người. Chỉ **một đích duy nhất**, client phân nhánh theo `event.type`:

```js
stompClient.subscribe("/user/queue/chat", (msg) => {
    const event = JSON.parse(msg.body);   // WsEvent { type, conversationId, payload }
    if (event.type === "MESSAGE_CREATED") { /* thêm tin vào UI */ }
    if (event.type === "READ_UPDATED")    { /* cập nhật đã đọc */ }
});
```

---

## 3. Định tuyến tới đúng người: Principal

Broker **không biết "nhóm" là gì** — nó chỉ biết "gửi cho user tên X". Danh tính X lấy từ
`UserIdHandshakeHandler`: đọc header `X-User-Id` lúc bắt tay và đặt làm **tên Principal** của session.

Nên `convertAndSendToUser("X", ...)` → broker gửi tới **mọi session** có principal = X
(mở nhiều tab thì cả mấy tab đều nhận).

---

## 4. A gửi tin → tới B thế nào

```
ChatFanout.onMessageCreated (chạy SAU commit, trên fanoutExecutor)
  1. findActiveUserIds(cid) → [A, B, ...]   (mọi thành viên ACTIVE, kể cả người gửi A)
  2. lặp từng user:
        convertAndSendToUser("B", "/queue/chat", WsEvent MESSAGE_CREATED)
                 ▼
        SimpleBroker: ghép /user + "B" → tìm session có principal = B
                 ▼
        Đẩy xuống dây WebSocket của từng session B đang mở
                 ▼
        STOMP client của B (đang subscribe) bắt lấy → render lên UI
```

- **Fan-out lặp từng người**, không broadcast vào "phòng". B nhận vì B nằm trong `findActiveUserIds`.
- **B online**: thấy tin gần như tức thì, khỏi refresh.
- **A cũng nhận** `MESSAGE_CREATED` (A nằm trong danh sách) — by-design; client A tự khử trùng theo
  `clientMessageId` so với tin đã nhận ở response REST `201`.

---

## 5. Nếu B offline — vì sao vẫn không mất tin

`convertAndSendToUser("B", ...)` khi B không có session nào → broker **không có đích, gói bị bỏ im lặng**
(không lỗi, không hàng đợi chờ). Tin vẫn an toàn trong DB. B **không** nhận lại bản WS đã lỡ; B **lấy bù qua REST**:

- `GET /conversations` → thấy `unread` tăng.
- `GET /conversations/{id}/messages?afterSeq=<seq cuối B đã có>` → kéo đủ tin đã lỡ (nhờ `seq` liên tục).

Tương tự, nếu fan-out lỗi thì `ChatFanout` chỉ **nuốt lỗi + log WARN**, vì tin đã commit và client lấy bù được.

> **Nguồn sự thật là REST + DB; WebSocket chỉ là thông báo realtime cho người đang online, hỏng cũng không sao.**

---

## 6. Giới hạn đã biết (một instance)

`SimpleBroker` giữ hàng đợi **trong bộ nhớ của chính instance** đang giữ kết nối của B. Hiện chạy một instance nên ổn.
Khi scale nhiều instance, session B ở instance khác sẽ không nhận được nếu chỉ đẩy nội bộ — cần broker relay/Redis.
Đây là lý do ADR-0004 dặn **không viết fan-out giả định chỉ có một instance**.
