package com.side_project.chat_service.domain;

import java.io.Serializable;
import java.util.UUID;

public record ConversationMemberId(UUID conversationId, String userId) implements Serializable {
}
