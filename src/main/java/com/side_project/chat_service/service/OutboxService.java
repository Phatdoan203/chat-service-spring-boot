package com.side_project.chat_service.service;

import java.util.List;

import com.side_project.chat_service.dto.response.OutboxPendingView;

/** Thao tac bookkeeping tren outbox trong transaction (chi service duoc mo transaction). */
public interface OutboxService {

    List<OutboxPendingView> findPending(int limit);

    void markPublished(long id);

    void recordFailure(long id, int maxAttempts);
}
