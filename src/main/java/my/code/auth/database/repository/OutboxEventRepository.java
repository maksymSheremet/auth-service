package my.code.auth.database.repository;

import my.code.auth.database.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop50ByProcessedFalseOrderByCreatedAtAsc();

    @Modifying
    @Transactional
    @Query("UPDATE OutboxEvent e SET e.processed = true, e.processedAt = :now WHERE e.id = :id")
    void markAsProcessed(Long id, Instant now);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.processed = true AND e.processedAt < :cutoff")
    int deleteProcessedBefore(Instant cutoff);
}
