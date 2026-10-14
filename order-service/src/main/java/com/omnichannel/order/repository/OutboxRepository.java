package com.omnichannel.order.repository;

import com.omnichannel.order.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(String status);

    @Query(value = "SELECT id FROM outbox_events WHERE status = :status ORDER BY created_at ASC LIMIT 50", nativeQuery = true)
    List<Long> findPendingEventIds(@Param("status") String status);

    @Query(value = "SELECT * FROM outbox_events WHERE id = :id AND status = 'PENDING' FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<OutboxEvent> findPendingByIdForUpdateSkipLocked(@Param("id") Long id);
}
