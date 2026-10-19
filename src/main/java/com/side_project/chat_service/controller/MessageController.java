package com.side_project.chat_service.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.side_project.chat_service.common.util.LogEvent;
import com.side_project.chat_service.dto.request.MarkReadRequest;
import com.side_project.chat_service.dto.request.SendMessageRequest;
import com.side_project.chat_service.dto.response.MessageResponse;
import com.side_project.chat_service.service.MessageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Messages", description = "Gui tin, doc lich su, danh dau da doc")
@RestController
@RequestMapping("/api/v1/conversations/{id}")
public class MessageController {

    private static final String ME = "Id nguoi goi (gateway chen; chua co auth nen client tu dat khi test)";

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @Operation(summary = "Gui tin", description = """
            Idempotent theo `(conversation, nguoi gui, clientMessageId)`. Lan dau tra 201; gui lai cung \
            `clientMessageId` tra 200 voi CUNG `messageId` va `seq`. Khong phai thanh vien ACTIVE tra 404.

            Kiem tra bang tay:
            ```
            curl -i -X POST localhost:8888/api/v1/conversations/$CID/messages \\
              -H 'X-User-Id: u-1' -H 'Content-Type: application/json' \\
              -d '{"clientMessageId":"c-7","type":"TEXT","body":"xin chao"}'
            ```
            1. Chay lenh tren hai lan: lan 1 `201`, lan 2 `200`, `messageId`/`seq` giong nhau.
            2. Gui song song 20 tin khac `clientMessageId` (`xargs -P 20`): `GET .../messages` phai thay \
            `seq` lien tuc 1..N, khong ho, khong trung (huy gui trung se rollback ca `last_seq+1`).
            3. Mo STOMP vao `/ws/chat` (header `X-User-Id`), subscribe `/user/queue/chat`: moi thanh vien \
            ACTIVE, ke ca nguoi gui, nhan `MESSAGE_CREATED`. Nguoi gui nhan hai lan (201 + WebSocket) la by-design.
            """)
    @ApiResponse(responseCode = "201", description = "Tin moi")
    @ApiResponse(responseCode = "200", description = "Trung clientMessageId, tra tin cu")
    @ApiResponse(responseCode = "404", description = "Khong phai thanh vien ACTIVE hoac hoi thoai khong ton tai")
    @ApiResponse(responseCode = "422", description = "TEXT thieu body, IMAGE/FILE thieu attachmentId, hoac body sai")
    @PostMapping("/messages")
    public ResponseEntity<MessageResponse> send(@Parameter(description = ME) @RequestHeader("X-User-Id") String me,
            @PathVariable UUID id, @Valid @RequestBody SendMessageRequest request) {
        var sent = service.send(me, id, request);
        var m = sent.message();
        log.info(LogEvent.of("message.sent", "conversationId", id, "messageId", m.messageId(), "seq", m.seq(),
                "duplicate", !sent.created()));
        return ResponseEntity.status(sent.created() ? HttpStatus.CREATED : HttpStatus.OK).body(m);
    }

    @Operation(summary = "Lich su / lay bu", description = """
            Keyset theo `seq`, cat tin truoc luc tham gia, bo tin da xoa mem. `limit` mac dinh 50, tran 200.
            - `afterSeq` co mat: lay bu, cu truoc (`seq > afterSeq`, tang dan).
            - Nguoc lai: cuon len, moi truoc (`seq < beforeSeq`, giam dan; bo trong = tin moi nhat).

            ```
            curl -H 'X-User-Id: u-1' 'localhost:8888/api/v1/conversations/$CID/messages?limit=20'
            curl -H 'X-User-Id: u-1' 'localhost:8888/api/v1/conversations/$CID/messages?afterSeq=185'
            ```
            Nguoi vua duoc them vao nhom khong thay tin co `seq <= join_seq`.
            """)
    @ApiResponse(responseCode = "404", description = "Khong phai thanh vien ACTIVE")
    @GetMapping("/messages")
    public List<MessageResponse> history(@Parameter(description = ME) @RequestHeader("X-User-Id") String me,
            @PathVariable UUID id, @RequestParam(required = false) Long beforeSeq,
            @RequestParam(required = false) Long afterSeq, @RequestParam(defaultValue = "50") int limit) {
        return service.history(me, id, beforeSeq, afterSeq, limit);
    }

    @Operation(summary = "Danh dau da doc toi seq", description = """
            Con tro chi tang: gui `seq` nho hon hoac bang con tro hien tai la no-op (van 200). \
            `seq` lon hon `lastSeq` cua hoi thoai tra 422.

            ```
            curl -i -X POST localhost:8888/api/v1/conversations/$CID/read \\
              -H 'X-User-Id: u-2' -H 'Content-Type: application/json' -d '{"seq":5}'
            ```
            Kiem tra: doc toi 5 roi doc lui toi 3 -> `unread` trong `GET /conversations` khong tang lai. \
            Neu con tro thuc su tang, cac session cua chinh nguoi doc nhan `READ_UPDATED`.
            """)
    @ApiResponse(responseCode = "404", description = "Khong phai thanh vien ACTIVE")
    @ApiResponse(responseCode = "422", description = "seq <= 0 hoac vuot lastSeq")
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/read")
    public void markRead(@Parameter(description = ME) @RequestHeader("X-User-Id") String me, @PathVariable UUID id,
            @Valid @RequestBody MarkReadRequest request) {
        service.markRead(me, id, request.seq());
    }
}
