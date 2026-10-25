package com.side_project.chat_service.messaging;

import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.side_project.chat_service.common.util.LogEvent;
import com.side_project.chat_service.dto.response.MessageCreatedEvent;
import com.side_project.chat_service.dto.response.ReadUpdatedEvent;
import com.side_project.chat_service.dto.response.WsEvent;
import com.side_project.chat_service.repository.ConversationMemberRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ChatFanout {

    private static final String DESTINATION = "/queue/chat";

    private final SimpMessagingTemplate ws;
    private final ConversationMemberRepository members;
    private final ThreadPoolTaskExecutor fanoutExecutor;

    public ChatFanout(SimpMessagingTemplate ws, ConversationMemberRepository members,
            ThreadPoolTaskExecutor fanoutExecutor) {
        this.ws = ws;
        this.members = members;
        this.fanoutExecutor = fanoutExecutor;
    }

    // shortcut: fanout truc tiep qua AFTER_COMMIT khi chua co Kafka; thay bang Kafka consumer khi Kafka len, xoa listener nay
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCreated(MessageCreatedEvent e) {
        fanoutExecutor.execute(() -> {
            try {
                // gui cho ca nguoi gui: client khu trung theo clientMessageId
                var out = new WsEvent<>("MESSAGE_CREATED", e.conversationId(), e);
                members.findActiveUserIds(e.conversationId()).forEach(u -> ws.convertAndSendToUser(u.toString(), DESTINATION, out));
            } catch (RuntimeException ex) {
                fail("message.fanout.failed", e.conversationId(), e.messageId(), ex);
            }
        });
    }

    // shortcut: nhu tren, READ_UPDATED chi toi cac session cua chinh nguoi doc
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReadUpdated(ReadUpdatedEvent e) {
        fanoutExecutor.execute(() -> {
            try {
                ws.convertAndSendToUser(e.userId().toString(), DESTINATION, new WsEvent<>("READ_UPDATED", e.conversationId(), e));
            } catch (RuntimeException ex) {
                fail("read.fanout.failed", e.conversationId(), null, ex);
            }
        });
    }

    // nuot loi: tin da commit, client se lay bu qua REST
    private static void fail(String event, UUID conversationId, UUID messageId, RuntimeException ex) {
        log.warn(LogEvent.of(event, "conversationId", conversationId, "messageId", messageId,
                "error", ex.getClass().getSimpleName()));
    }
}
