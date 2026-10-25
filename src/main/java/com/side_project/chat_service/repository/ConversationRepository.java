package com.side_project.chat_service.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.side_project.chat_service.domain.Conversation;
import com.side_project.chat_service.dto.response.ConversationListView;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByDmKey(String dmKey);

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO conversation (id, type, dm_key, created_by)
            VALUES (:id, 'DM', :dmKey, :createdBy)
            ON CONFLICT (dm_key) DO NOTHING""")
    int insertDmIfAbsent(@Param("id") UUID id, @Param("dmKey") String dmKey, @Param("createdBy") UUID createdBy);

    // Mot query, khong N+1.
    // shortcut: cursor theo thoi gian, hai hoi thoai trung mili-giay co the sot o ranh gioi trang, nang len keyset (time, id) khi can
    @Query("""
            SELECT c.id AS id, CAST(c.type AS string) AS type, c.dmKey AS dmKey, c.title AS title,
                   c.lastSeq AS lastSeq, c.lastMessageId AS lastMessageId,
                   c.lastMessageAt AS lastMessageAt, c.createdAt AS createdAt
            FROM Conversation c, ConversationMember m
            WHERE m.conversationId = c.id AND m.userId = :me AND m.status = 'ACTIVE'
              AND COALESCE(c.lastMessageAt, c.createdAt) < :before
            ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC""")
    List<ConversationListView> findMine(@Param("me") UUID me, @Param("before") Instant before, Pageable page);

    // Cap seq: KHONG them @Modifying, neu khong se mat gia tri RETURNING. Khoa dong toi het transaction (ADR-0005).
    @Query(nativeQuery = true, value = """
            UPDATE conversation SET last_seq = last_seq + 1, last_message_at = now()
            WHERE id = :id RETURNING last_seq""")
    Long nextSeq(@Param("id") UUID id);

    @Modifying
    @Query(nativeQuery = true, value = "UPDATE conversation SET last_message_id = :msgId WHERE id = :id")
    int setLastMessage(@Param("id") UUID id, @Param("msgId") UUID messageId);
}
