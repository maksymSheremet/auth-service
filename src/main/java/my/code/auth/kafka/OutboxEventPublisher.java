package my.code.auth.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import my.code.auth.database.entity.OutboxEvent;
import my.code.auth.database.repository.OutboxEventRepository;
import org.springframework.stereotype.Component;

/**
 * Saves events to the outbox table within the caller's transaction.
 * Actual Kafka publishing is handled by {@link OutboxProcessor}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Stores an event in the outbox table.
     * Must be called within an existing @Transactional context.
     *
     * @param aggregateId identifies the aggregate (e.g. userId)
     * @param eventType   event name (e.g. "USER_REGISTERED")
     * @param payload     event object to serialize as JSON
     */
    public void publish(String aggregateId, String eventType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);

            OutboxEvent event = OutboxEvent.builder()
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(json)
                    .build();

            outboxEventRepository.save(event);
            log.debug("Saved outbox event: type={}, aggregateId={}", eventType, aggregateId);

        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize outbox event payload", e);
        }
    }
}
