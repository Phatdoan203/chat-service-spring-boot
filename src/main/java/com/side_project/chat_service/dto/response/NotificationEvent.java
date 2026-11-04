package com.side_project.chat_service.dto.response;

import java.util.UUID;

/**
 * Hop dong gui sang service processor (E:\kafka-project\processor). Dung dung 11 field processor can,
 * khong dư field. Hai diem bat buoc:
 * - schemaVersion LUON = 1, gui tuong minh (processor dung @Builder.Default + @NoArgsConstructor nen
 *   thieu field nay se ra 0 va bi loai UNSUPPORTED_SCHEMA_VERSION).
 * - occurredAt la String ISO-8601 local, KHONG offset/'Z' (processor parse LocalDateTime).
 */
public record NotificationEvent(
                UUID eventId,
                String occurredAt,
                int schemaVersion,
                String type,
                String recipient,
                String recipientName,
                String subject,
                String content,
                String referenceId,
                String referenceType,
                String priority) {
}
