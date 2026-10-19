package com.side_project.chat_service.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Payload MessageCreated: dung chung cho outbox, application event va WebSocket. Chua body, khong duoc log. */
public record MessageCreatedEvent(
        UUID conversationId, UUID messageId, long seq, String senderId, String type, String body,
        String clientMessageId, Instant createdAt) {
}
