package com.side_project.chat_service.messaging;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.side_project.chat_service.common.util.LogEvent;
import com.side_project.chat_service.dto.response.MessageCreatedEvent;
import com.side_project.chat_service.dto.response.NotificationEvent;
import com.side_project.chat_service.dto.response.OutboxPendingView;
import com.side_project.chat_service.service.NotificationService;
import com.side_project.chat_service.service.OutboxService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Doc outbox_event chua publish va day notification thang sang processor (push-events / inapp-events).
 * Outbox giu bat bien ADR-0005 (khong publish Kafka trong transaction gui tin) va ADR-0002 (khong mat
 * event khi process chet sau commit). WS fan-out da do ChatFanout lo, nen o day chi xu ly thong bao.
 *
 * Mot luong duy nhat (@Scheduled fixedDelay) + gate 1 instance (chat.outbox.publisher.enabled).
 * shortcut: khi scale nhieu instance -> claim bang SELECT ... FOR UPDATE SKIP LOCKED hoac leader election.
 */
@Slf4j
@Component
public class OutboxPublisher {

    private static final int BATCH = 100;
    private static final long SEND_TIMEOUT_SECONDS = 10;
    private static final String MESSAGE_CREATED = "MessageCreated";

    private final OutboxService outbox;
    private final NotificationService notifications;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final boolean enabled;
    private final String inappTopic;
    private final String pushTopic;

    public OutboxPublisher(OutboxService outbox, NotificationService notifications,
            KafkaTemplate<String, String> kafka, ObjectMapper json,
            @Value("${chat.outbox.publisher.enabled:true}") boolean enabled,
            @Value("${chat.notification.topics.inapp}") String inappTopic,
            @Value("${chat.notification.topics.push}") String pushTopic) {
        this.outbox = outbox;
        this.notifications = notifications;
        this.kafka = kafka;
        this.json = json;
        this.enabled = enabled;
        this.inappTopic = inappTopic;
        this.pushTopic = pushTopic;
    }

    @Scheduled(fixedDelayString = "${chat.outbox.publisher.interval-ms:200}")
    public void publishPending() {
        if (!enabled) {
            return;
        }
        for (OutboxPendingView row : outbox.findPending(BATCH)) {
            if (!MESSAGE_CREATED.equals(row.getEventType())) {
                outbox.markPublished(row.getId()); // khong phai nguon thong bao: bo qua, khong chan dong sau
                continue;
            }
            MessageCreatedEvent event = parse(row);
            if (event == null) {
                continue; // dong hong da tombstone trong parse()
            }
            if (relay(event)) {
                outbox.markPublished(row.getId());
            } else {
                return; // loi tam thoi: dung sweep, giu thu tu, thu lai tick sau (khong bump attempts)
            }
        }
    }

    // true = da gui xong (ke ca bo qua loi vinh vien); false = gap loi tam thoi, can thu lai ca dong.
    private boolean relay(MessageCreatedEvent event) {
        for (NotificationEvent notification : notifications.buildFor(event)) {
            String topic = topicFor(notification.type());
            if (topic == null) {
                log.warn(LogEvent.of("notification.channel.unknown", "type", notification.type(),
                        "messageId", event.messageId()));
                continue;
            }
            try {
                kafka.send(topic, notification.recipient(), json.writeValueAsString(notification))
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (ExecutionException | TimeoutException e) {
                if (isPermanent(e.getCause())) {
                    log.error(LogEvent.of("notification.publish.poison", "messageId", event.messageId(),
                            "error", causeName(e)));
                    continue; // khong gui duoc vinh vien: bo qua recipient nay
                }
                log.warn(LogEvent.of("notification.publish.retry", "messageId", event.messageId(),
                        "error", causeName(e)));
                return false;
            }
        }
        log.info(LogEvent.of("notification.dispatched", "conversationId", event.conversationId(),
                "messageId", event.messageId(), "seq", event.seq()));
        return true;
    }

    private MessageCreatedEvent parse(OutboxPendingView row) {
        try {
            return json.readValue(row.getPayload(), MessageCreatedEvent.class);
        } catch (RuntimeException e) {
            // Payload hong deterministic (parse lai van hong): tombstone NGAY (maxAttempts=1) de roi khoi
            // tap chua-publish, khong chan dong sau, khong reparse vo ich.
            outbox.recordFailure(row.getId(), 1);
            log.error(LogEvent.of("outbox.payload.poison", "outboxId", row.getId(),
                    "error", e.getClass().getSimpleName()));
            return null;
        }
    }

    private String topicFor(String type) {
        return switch (type) {
            case "INAPP" -> inappTopic;
            case "PUSH" -> pushTopic;
            default -> null;
        };
    }

    private static boolean isPermanent(Throwable cause) {
        return cause instanceof RecordTooLargeException || cause instanceof SerializationException;
    }

    private static String causeName(Exception e) {
        return e.getCause() == null ? e.getClass().getSimpleName() : e.getCause().getClass().getSimpleName();
    }
}
