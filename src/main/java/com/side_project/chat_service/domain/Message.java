package com.side_project.chat_service.domain;

import java.time.Instant;
import java.util.UUID;

import com.side_project.chat_service.enums.MessageType;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

// Chi doc qua entity; INSERT di bang native query de khong bi merge() chen them mot SELECT.
@Entity
@Table(name = "message")
@Getter
@NoArgsConstructor
public class Message {

    @Id
    private UUID id;
    private UUID conversationId;
    private long seq;
    private String senderId;

    @Enumerated(EnumType.STRING)
    private MessageType type;

    private String body;
    private String attachmentId;
    private String clientMessageId;
    private Instant createdAt;
    private Instant deletedAt;
}
