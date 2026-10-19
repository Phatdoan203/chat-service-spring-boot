package com.side_project.chat_service.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.side_project.chat_service.common.exception.ApiException;
import com.side_project.chat_service.common.exception.ErrorCode;
import com.side_project.chat_service.domain.ConversationMember;
import com.side_project.chat_service.domain.OutboxEvent;
import com.side_project.chat_service.dto.request.SendMessageRequest;
import com.side_project.chat_service.dto.response.MessageCreatedEvent;
import com.side_project.chat_service.dto.response.MessageResponse;
import com.side_project.chat_service.dto.response.ReadUpdatedEvent;
import com.side_project.chat_service.enums.MemberStatus;
import com.side_project.chat_service.enums.MessageType;
import com.side_project.chat_service.repository.ConversationMemberRepository;
import com.side_project.chat_service.repository.ConversationRepository;
import com.side_project.chat_service.repository.MessageRepository;
import com.side_project.chat_service.repository.OutboxEventRepository;
import com.side_project.chat_service.service.MessageService;

import tools.jackson.databind.ObjectMapper;

@Service
public class MessageServiceImpl implements MessageService {

    private static final int MAX_LIMIT = 200;
    private static final int DEFAULT_LIMIT = 50;

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final OutboxEventRepository outbox;
    private final ApplicationEventPublisher events;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public MessageServiceImpl(ConversationRepository conversations, ConversationMemberRepository members,
            MessageRepository messages, OutboxEventRepository outbox, ApplicationEventPublisher events,
            ObjectMapper json, PlatformTransactionManager txManager) {
        this.conversations = conversations;
        this.members = members;
        this.messages = messages;
        this.outbox = outbox;
        this.events = events;
        this.json = json;
        this.tx = new TransactionTemplate(txManager);
    }

    // Co y KHONG @Transactional: tx gui va tx doc-lai la hai transaction rieng. Vi pham unique lam Postgres
    // abort tx gui, nen SELECT tin cu phai nam ngoai no; rollback tron ven cung huy luon last_seq+1 (khong ho seq).
    @Override
    public Sent send(String me, UUID conversationId, SendMessageRequest request) {
        boolean missing = request.type() == MessageType.TEXT
                ? request.body() == null || request.body().isBlank()
                : request.attachmentId() == null || request.attachmentId().isBlank();
        if (missing) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "TEXT can body; IMAGE/FILE can attachmentId");
        }
        try {
            return new Sent(tx.execute(s -> insertMessage(me, conversationId, request)), true);
        } catch (DataIntegrityViolationException e) {
            var existing = tx.execute(s -> messages
                    .findByConversationIdAndSenderIdAndClientMessageId(conversationId, me, request.clientMessageId())
                    .map(MessageResponse::from));
            // khong thay tin cu => khong phai trung idempotency, la loi rang buoc khac
            return new Sent(existing.orElseThrow(() -> e), false);
        }
    }

    private MessageResponse insertMessage(String me, UUID cid, SendMessageRequest r) {
        requireActive(cid, me);
        Long seq = conversations.nextSeq(cid); // khoa dong conversation tu day
        if (seq == null) {
            throw new ApiException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        String type = r.type().name();
        messages.insert(id, cid, seq, me, type, r.body(), r.attachmentId(), r.clientMessageId(), now);
        conversations.setLastMessage(cid, id);
        members.bumpCursors(cid, me, seq);

        var event = new MessageCreatedEvent(cid, id, seq, me, type, r.body(), r.clientMessageId(), now);
        outbox.save(new OutboxEvent(cid, "MessageCreated", json.writeValueAsString(event)));
        events.publishEvent(event);
        return new MessageResponse(id, cid, seq, me, r.clientMessageId(), type, r.body(), now);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> history(String me, UUID conversationId, Long beforeSeq, Long afterSeq, int limit) {
        long joinSeq = requireActive(conversationId, me).getJoinSeq();
        var page = PageRequest.of(0, Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT));
        var found = afterSeq != null
                ? messages.findAfter(conversationId, Math.max(afterSeq, joinSeq), page)
                : messages.findBefore(conversationId, joinSeq, beforeSeq == null ? Long.MAX_VALUE : beforeSeq, page);
        return found.stream().map(MessageResponse::from).toList();
    }

    @Override
    @Transactional
    public void markRead(String me, UUID conversationId, long seq) {
        requireActive(conversationId, me);
        var conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new ApiException(ErrorCode.CONVERSATION_NOT_FOUND));
        if (seq > conversation.getLastSeq()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "seq vuot qua tin moi nhat cua hoi thoai");
        }
        if (members.advanceRead(conversationId, me, seq) == 1) {
            events.publishEvent(new ReadUpdatedEvent(conversationId, me, seq));
        }
    }

    private ConversationMember requireActive(UUID conversationId, String userId) {
        return members.findByConversationIdAndUserIdAndStatus(conversationId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_A_MEMBER));
    }
}
