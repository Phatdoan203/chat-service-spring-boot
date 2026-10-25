package com.side_project.chat_service.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.side_project.chat_service.enums.ConversationType;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "conversation")
@Getter
@NoArgsConstructor
public class Conversation {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private ConversationType type;

    private String dmKey;
    private String title;
    private UUID createdBy;

    // last_seq/last_message_* chi doi bang UPDATE native, entity khong bao gio ghi de
    @Column(updatable = false)
    private long lastSeq;

    @Column(updatable = false)
    private UUID lastMessageId;

    @Column(updatable = false)
    private Instant lastMessageAt;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    public Conversation(UUID id, ConversationType type, String dmKey, String title, UUID createdBy) {
        this.id = id;
        this.type = type;
        this.dmKey = dmKey;
        this.title = title;
        this.createdBy = createdBy;
    }
}
