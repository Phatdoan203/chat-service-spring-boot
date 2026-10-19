package com.side_project.chat_service.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private UUID aggregateId;

    @Column(updatable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private String payload;

    // aggregate_type, created_at, published_at, attempts lay default cua DB
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    public OutboxEvent(UUID aggregateId, String eventType, String payload) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
    }
}
