package com.side_project.chat_service.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import com.side_project.chat_service.enums.MemberRole;
import com.side_project.chat_service.enums.MemberStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "conversation_member")
@IdClass(ConversationMemberId.class)
@Getter
@NoArgsConstructor
public class ConversationMember {

    @Id
    private UUID conversationId;

    @Id
    private String userId;

    @Enumerated(EnumType.STRING)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    private MemberStatus status = MemberStatus.ACTIVE;

    private long joinSeq;

    // hai con tro chi doi bang UPDATE native (tang don dieu), entity khong ghi de
    @Column(updatable = false)
    private long lastDeliveredSeq;

    @Column(updatable = false)
    private long lastReadSeq;

    @Column(insertable = false, updatable = false)
    private Instant joinedAt;

    @Column(updatable = false)
    private Instant leftAt;

    public ConversationMember(UUID conversationId, String userId, MemberRole role, long joinSeq) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.role = role;
        this.joinSeq = joinSeq;
        // nguoi moi vao: unread = 0 (04-du-lieu-va-api.md)
        this.lastDeliveredSeq = joinSeq;
        this.lastReadSeq = joinSeq;
    }
}
