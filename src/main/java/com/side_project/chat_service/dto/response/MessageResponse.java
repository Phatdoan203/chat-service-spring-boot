package com.side_project.chat_service.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.side_project.chat_service.domain.Message;

public record MessageResponse(
        UUID messageId, UUID conversationId, long seq, UUID senderId, String clientMessageId,
        String type, String body, Instant createdAt) {

    public static MessageResponse from(Message m) {
        return new MessageResponse(m.getId(), m.getConversationId(), m.getSeq(), m.getSenderId(),
                m.getClientMessageId(), m.getType().name(), m.getBody(), m.getCreatedAt());
    }
}
