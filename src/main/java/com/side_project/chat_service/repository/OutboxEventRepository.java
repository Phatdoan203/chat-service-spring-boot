package com.side_project.chat_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.side_project.chat_service.domain.OutboxEvent;
import com.side_project.chat_service.dto.response.OutboxPendingView;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // Doc theo id => dung thu tu phat (ADR-0002 lop 2). payload::text de jsonb ve String.
    @Query(nativeQuery = true, value = """
            SELECT id, aggregate_id, event_type, payload::text AS payload, attempts
            FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY id
            LIMIT :limit""")
    List<OutboxPendingView> findUnpublished(@Param("limit") int limit);

    @Modifying
    @Query(nativeQuery = true, value = "UPDATE outbox_event SET published_at = now() WHERE id = :id")
    int markPublished(@Param("id") long id);

    // Chi goi cho loi vinh vien (vd record qua lon). Khi attempts cham nguong thi tombstone
    // (set published_at) de dong roi khoi outbox_event_unpublished_idx, khong chan cac dong sau.
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE outbox_event
            SET attempts = attempts + 1,
                published_at = CASE WHEN attempts + 1 >= :maxAttempts THEN now() ELSE published_at END
            WHERE id = :id""")
    int recordFailure(@Param("id") long id, @Param("maxAttempts") int maxAttempts);
}
