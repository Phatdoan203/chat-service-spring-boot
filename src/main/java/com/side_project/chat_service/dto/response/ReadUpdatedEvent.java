package com.side_project.chat_service.dto.response;

import java.util.UUID;

public record ReadUpdatedEvent(UUID conversationId, String userId, long lastReadSeq) {
}
