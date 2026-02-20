package my.code.auth.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.code.auth.database.entity.OutboxEvent;
import my.code.auth.database.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Polls the outbox table and publishes pending events to Kafka.
 * Runs on a fixed schedule — guarantees at-least-once delivery.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Maps event types to Kafka topics.
     */
    private final Map<String, String> eventTopicMapping = Map.of(
            "USER_REGISTERED", "user-registered-events"
    );

    @Value("${kafka.outbox.cleanup-days:7}")
    private int cleanupDays;

    /**
     * Polls unprocessed events every 5 seconds and sends to Kafka.
     */
    @Scheduled(fixedDelayString = "${kafka.outbox.poll-interval-ms:5000}")
    public void processOutbox() {
        List<OutboxEvent> events = outboxEventRepository.findTop50ByProcessedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : events) {
            try {
                String topic = resolveTopicFor(event.getEventType());
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload()).get();

                markAsProcessed(event.getId());
                log.debug("Published outbox event id={}, type={}", event.getId(), event.getEventType());

            } catch (Exception e) {
                log.error("Failed to publish outbox event id={}, type={}: {}",
                        event.getId(), event.getEventType(), e.getMessage());
                break; // stop processing to preserve ordering
            }
        }
    }

    /**
     * Cleans up old processed events. Runs daily at 3 AM.
     */
    @Scheduled(cron = "${kafka.outbox.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanupProcessedEvents() {
        Instant cutoff = Instant.now().minus(cleanupDays, ChronoUnit.DAYS);
        int deleted = outboxEventRepository.deleteProcessedBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} processed outbox events older than {} days", deleted, cleanupDays);
        }
    }

    protected void markAsProcessed(Long eventId) {
        outboxEventRepository.markAsProcessed(eventId, Instant.now());
    }

    private String resolveTopicFor(String eventType) {
        String topic = eventTopicMapping.get(eventType);
        if (topic == null) {
            throw new IllegalStateException("No topic mapping for event type: " + eventType);
        }
        return topic;
    }
}
