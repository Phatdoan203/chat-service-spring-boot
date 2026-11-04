package com.side_project.chat_service.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.side_project.chat_service.domain.User;
import com.side_project.chat_service.dto.response.MessageCreatedEvent;
import com.side_project.chat_service.dto.response.NotificationEvent;
import com.side_project.chat_service.repository.ConversationMemberRepository;
import com.side_project.chat_service.repository.UserRepository;
import com.side_project.chat_service.service.NotificationService;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final int PREVIEW_MAX = 200;
    private static final String REFERENCE_TYPE = "CHAT_MESSAGE";
    private static final String PRIORITY = "NORMAL";
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final ConversationMemberRepository members;
    private final UserRepository users;
    private final boolean pushEnabled;
    private final boolean inappEnabled;

    public NotificationServiceImpl(ConversationMemberRepository members, UserRepository users,
            @Value("${chat.notification.push-enabled:true}") boolean pushEnabled,
            @Value("${chat.notification.inapp-enabled:false}") boolean inappEnabled) {
        this.members = members;
        this.users = users;
        this.pushEnabled = pushEnabled;
        this.inappEnabled = inappEnabled;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationEvent> buildFor(MessageCreatedEvent event) {
        var recipients = members.findActiveUserIds(event.conversationId()).stream()
                .filter(userId -> !userId.equals(event.senderId()))
                .toList();
        if (recipients.isEmpty()) {
            return List.of();
        }

        String senderName = users.findById(event.senderId()).map(User::getDisplayName).orElse(null);
        String subject = senderName == null ? "Tin nhan moi" : "Tin nhan moi tu " + senderName;
        String content = preview(event.type(), event.body());
        String occurredAt = LocalDateTime.ofInstant(event.createdAt(), ZONE).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String referenceId = event.messageId().toString();

        var out = new ArrayList<NotificationEvent>(recipients.size() * 2);
        for (UUID userId : recipients) {
            if (inappEnabled) {
                out.add(build(userId, "INAPP", occurredAt, subject, content, referenceId));
            }
            if (pushEnabled) {
                out.add(build(userId, "PUSH", occurredAt, subject, content, referenceId));
            }
        }
        return out;
    }

    // eventId tat dinh theo (messageId, userId, kenh): redeliver at-least-once -> processor khu theo eventId.
    private static NotificationEvent build(UUID userId, String channel, String occurredAt, String subject,
            String content, String referenceId) {
        UUID eventId = UUID.nameUUIDFromBytes((referenceId + ":" + userId + ":" + channel).getBytes(StandardCharsets.UTF_8));
        return new NotificationEvent(eventId, occurredAt, 1, channel, userId.toString(), null,
                subject, content, referenceId, REFERENCE_TYPE, PRIORITY);
    }

    // content phai non-blank (processor bat buoc). Tin IMAGE/FILE co body null.
    private static String preview(String type, String body) {
        return switch (type) {
            case "IMAGE" -> "Da gui mot anh";
            case "FILE" -> "Da gui mot tep";
            case "TEXT" -> body == null || body.isBlank() ? "Tin nhan moi"
                    : body.length() <= PREVIEW_MAX ? body : body.substring(0, PREVIEW_MAX);
            default -> "Tin nhan moi";
        };
    }
}
