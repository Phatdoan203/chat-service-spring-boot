package com.side_project.chat_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.side_project.chat_service.domain.ConversationMember;
import com.side_project.chat_service.domain.ConversationMemberId;
import com.side_project.chat_service.enums.MemberStatus;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {

    // DM moi tao nen last_seq = 0 => join_seq = 0 (cac con tro lay default 0)
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO conversation_member (conversation_id, user_id, role, join_seq)
            VALUES (:cid, :a, 'MEMBER', 0), (:cid, :b, 'MEMBER', 0)""")
    int insertDmMembers(@Param("cid") UUID conversationId, @Param("a") String a, @Param("b") String b);

    Optional<ConversationMember> findByConversationIdAndUserIdAndStatus(UUID conversationId, String userId,
            MemberStatus status);

    @Query("SELECT m.userId FROM ConversationMember m WHERE m.conversationId = :cid AND m.status = 'ACTIVE'")
    List<String> findActiveUserIds(@Param("cid") UUID conversationId);

    // Keo ca delivered len, neu khong vo CHECK (last_read_seq <= last_delivered_seq).
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE conversation_member
            SET last_delivered_seq = GREATEST(last_delivered_seq, :seq), last_read_seq = GREATEST(last_read_seq, :seq)
            WHERE conversation_id = :cid AND user_id = :uid""")
    int bumpCursors(@Param("cid") UUID conversationId, @Param("uid") String userId, @Param("seq") long seq);

    // Chi tang: tra 0 dong khi seq khong moi hon con tro hien tai.
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE conversation_member
            SET last_read_seq = :seq, last_delivered_seq = GREATEST(last_delivered_seq, :seq)
            WHERE conversation_id = :cid AND user_id = :uid AND last_read_seq < :seq""")
    int advanceRead(@Param("cid") UUID conversationId, @Param("uid") String userId, @Param("seq") long seq);
}
