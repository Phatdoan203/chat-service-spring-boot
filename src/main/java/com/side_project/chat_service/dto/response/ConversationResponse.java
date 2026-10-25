package com.side_project.chat_service.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.side_project.chat_service.domain.Conversation;

import io.swagger.v3.oas.annotations.media.Schema;

public record ConversationResponse(
        @Schema(example = "3f0a7c92-5d31-4e88-9a12-7b6c5d4e3f2a") UUID id,
        @Schema(allowableValues = {"DM", "GROUP"}) String type,
        @Schema(description = "Chi co voi DM, null voi GROUP", example = "6f1c2a4e-8b7d-4e5a-9c3f-1a2b3c4d5e6f:9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d") String dmKey,
        @Schema(description = "Chi co voi GROUP, null voi DM", example = "Van hanh kho HCM") String title,
        @Schema(description = "seq cua tin moi nhat; 0 neu chua co tin", example = "0") long lastSeq,
        @Schema(description = "null neu chua co tin") UUID lastMessageId,
        @Schema(description = "null neu chua co tin") Instant lastMessageAt,
        Instant createdAt) {

    public static ConversationResponse from(Conversation c) {
        return new ConversationResponse(c.getId(), c.getType().name(), c.getDmKey(), c.getTitle(),
                c.getLastSeq(), c.getLastMessageId(), c.getLastMessageAt(), c.getCreatedAt());
    }

    public static ConversationResponse from(ConversationListView v) {
        return new ConversationResponse(v.getId(), v.getType(), v.getDmKey(), v.getTitle(),
                v.getLastSeq(), v.getLastMessageId(), v.getLastMessageAt(), v.getCreatedAt());
    }
}
