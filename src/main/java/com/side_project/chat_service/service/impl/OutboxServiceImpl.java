package com.side_project.chat_service.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.side_project.chat_service.dto.response.OutboxPendingView;
import com.side_project.chat_service.repository.OutboxEventRepository;
import com.side_project.chat_service.service.OutboxService;

@Service
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventRepository outbox;

    public OutboxServiceImpl(OutboxEventRepository outbox) {
        this.outbox = outbox;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxPendingView> findPending(int limit) {
        return outbox.findUnpublished(limit);
    }

    @Override
    @Transactional
    public void markPublished(long id) {
        outbox.markPublished(id);
    }

    @Override
    @Transactional
    public void recordFailure(long id, int maxAttempts) {
        outbox.recordFailure(id, maxAttempts);
    }
}
