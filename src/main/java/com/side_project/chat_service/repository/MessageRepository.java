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

import com.side_project.chat_service.domain.Message;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO message (id, conversation_id, seq, sender_id, type, body, attachment_id, client_message_id, created_at)
            VALUES (:id, :cid, :seq, :sender, :type, :body, :attachmentId, :clientMessageId, :createdAt)""")
    int insert(@Param("id") UUID id, @Param("cid") UUID conversationId, @Param("seq") long seq,
            @Param("sender") String senderId, @Param("type") String type, @Param("body") String body,
            @Param("attachmentId") String attachmentId, @Param("clientMessageId") String clientMessageId,
            @Param("createdAt") Instant createdAt);

    Optional<Message> findByConversationIdAndSenderIdAndClientMessageId(UUID conversationId, String senderId,
            String clientMessageId);

    // Cuon len: seq trong (after, before), moi nhat truoc.
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :cid AND m.seq > :after AND m.seq < :before AND m.deletedAt IS NULL
            ORDER BY m.seq DESC""")
    List<Message> findBefore(@Param("cid") UUID cid, @Param("after") long after, @Param("before") long before,
            Pageable page);

    // Lay bu: seq > after, cu truoc.
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :cid AND m.seq > :after AND m.deletedAt IS NULL
            ORDER BY m.seq ASC""")
    List<Message> findAfter(@Param("cid") UUID cid, @Param("after") long after, Pageable page);
}
