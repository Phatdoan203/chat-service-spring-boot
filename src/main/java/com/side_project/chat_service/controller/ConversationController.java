package com.side_project.chat_service.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.side_project.chat_service.dto.request.CreateConversationRequest;
import com.side_project.chat_service.dto.response.ConversationResponse;
import com.side_project.chat_service.service.ConversationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Conversations", description = "Tao hoi thoai va lay danh sach hoi thoai cua toi")
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

        private static final String ME = "Id nguoi goi (gateway chen; chua co auth nen client tu dat khi test)";

        private final ConversationService service;

        public ConversationController(ConversationService service) {
                this.service = service;
        }

        @Operation(summary = "Tao nhom hoac mo DM", description = """
                        Phan biet bang field `type` trong body: `DM` hoac `GROUP`. \
                        DM la idempotent: moi cap nguoi dung chi co dung mot DM, bat ke ai mo truoc. \
                        Mo DM lan dau tra 201; mo lai (hoac mo dong thoi, hoac phia ben kia mo) tra 200 voi \
                        cung `id`. Tao nhom luon tra 201 va moi lan goi la mot nhom moi.""")
        @ApiResponse(responseCode = "201", description = "Vua tao moi (DM lan dau, hoac nhom)")
        @ApiResponse(responseCode = "200", description = "DM da ton tai, tra lai hoi thoai cu")
        @ApiResponse(responseCode = "422", description = "Body sai (thieu/rong/qua dai) hoac tu nhan tin voi chinh minh", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
        @PostMapping
        public ResponseEntity<ConversationResponse> create(
                        @Parameter(description = ME) @RequestHeader("X-User-Id") UUID me,
                        @Valid @RequestBody CreateConversationRequest request) {
                var opened = service.create(me, request);
                return ResponseEntity.status(opened.created() ? HttpStatus.CREATED : HttpStatus.OK)
                                .body(opened.conversation());
        }

        @Operation(summary = "Danh sach hoi thoai cua toi", description = "Chi gom hoi thoai ma toi la thanh vien ACTIVE, moi nhat truoc. "
                        + "Phan trang bang `beforeAt` = `lastMessageAt` (hoac `createdAt` neu chua co tin) cua phan tu cuoi trang truoc. "
                        + "Chua co tin cuoi dang day du va unread.")
        @ApiResponse(responseCode = "200", description = "Mang rong neu chua co hoi thoai nao")
        @GetMapping
        public List<ConversationResponse> listMine(@Parameter(description = ME) @RequestHeader("X-User-Id") UUID me,
                        @Parameter(description = "Toi da 100; gia tri ngoai khoang bi kep") @RequestParam(defaultValue = "30") int limit,
                        @Parameter(description = "Cursor, ISO-8601 UTC. Bo trong = trang dau") @RequestParam(required = false) Instant beforeAt) {
                return service.listMine(me, limit, beforeAt);
        }
}
