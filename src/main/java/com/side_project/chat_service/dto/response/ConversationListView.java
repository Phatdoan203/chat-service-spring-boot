package com.side_project.chat_service.dto.response;

import java.time.Instant;
import java.util.UUID;

public interface ConversationListView {
    UUID getId();
    String getType();
    String getDmKey();
    String getTitle();
    long getLastSeq();
    UUID getLastMessageId();
    Instant getLastMessageAt();
    Instant getCreatedAt();
}
