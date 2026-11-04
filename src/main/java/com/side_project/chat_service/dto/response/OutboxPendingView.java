package com.side_project.chat_service.dto.response;

import java.util.UUID;

/** Projection cho OutboxPublisher doc dong chua publish: them attempts de quyet dinh tombstone. */
public interface OutboxPendingView {

    long getId();

    UUID getAggregateId();

    String getEventType();

    String getPayload();

    int getAttempts();
}
