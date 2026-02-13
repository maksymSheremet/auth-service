package my.code.auth.database.repository;

import my.code.auth.database.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Fetches a batch of unprocessed events ordered by creation time.
     */
    List<OutboxEvent> findTop50ByProcessedFalseOrderByCreatedAtAsc();

    /**
     * Marks an event as processed.
     */
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.processed = true, e.processedAt = :now WHERE e.id = :id")
    void markAsProcessed(Long id, Instant now);

    /**
     * Cleanup: removes processed events older than the given cutoff.
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.processed = true AND e.processedAt < :cutoff")
    int deleteProcessedBefore(Instant cutoff);
}
