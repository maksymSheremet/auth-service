package my.code.auth.kafka;

import my.code.auth.BaseIntegrationTest;
import my.code.auth.database.entity.OutboxEvent;
import my.code.auth.database.repository.OutboxEventRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class OutboxProcessorIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OutboxProcessor outboxProcessor;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private static final String USER_REGISTERED_TOPIC = "user-registered-events";

    @BeforeEach
    void cleanDb() {
        outboxEventRepository.deleteAll();
    }

    @Test
    @DisplayName("unprocessed event → processOutbox() → message in Kafka + marked processed")
    void processOutbox_sendsToKafkaAndMarksProcessed() {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateId("user-42")
                .eventType("USER_REGISTERED")
                .payload("{\"userId\":42,\"email\":\"test@test.com\"}")
                .build();
        outboxEventRepository.saveAndFlush(event);

        Map<org.apache.kafka.common.TopicPartition, Long> offsetsBefore = getCurrentEndOffsets();

        outboxProcessor.processOutbox();

        OutboxEvent processed = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertTrue(processed.isProcessed(), "Event should be marked as processed");
        assertNotNull(processed.getProcessedAt(), "processedAt should be set");

        List<ConsumerRecord<String, String>> records = pollKafkaTopicFrom(offsetsBefore, 1);
        assertFalse(records.isEmpty(), "Should receive at least one Kafka message");

        ConsumerRecord<String, String> kafkaRecord = records.getFirst();
        assertEquals("user-42", kafkaRecord.key());
        assertTrue(kafkaRecord.value().contains("\"userId\":42"));
    }

    @Test
    @DisplayName("multiple events → processOutbox() → all sent in order")
    void processOutbox_multipleEvents_sentInOrder() {
        for (int i = 1; i <= 3; i++) {
            outboxEventRepository.saveAndFlush(OutboxEvent.builder()
                    .aggregateId("user-" + i)
                    .eventType("USER_REGISTERED")
                    .payload("{\"userId\":" + i + "}")
                    .build());
        }

        Map<org.apache.kafka.common.TopicPartition, Long> offsetsBefore = getCurrentEndOffsets();

        outboxProcessor.processOutbox();

        List<OutboxEvent> all = outboxEventRepository.findAll();
        assertTrue(all.stream().allMatch(OutboxEvent::isProcessed));

        List<ConsumerRecord<String, String>> records = pollKafkaTopicFrom(offsetsBefore, 3);
        assertEquals(3, records.size());
    }

    @Test
    @DisplayName("no unprocessed events → processOutbox() does nothing")
    void processOutbox_noEvents_doesNothing() {
        assertDoesNotThrow(() -> outboxProcessor.processOutbox());
    }

    @Test
    @DisplayName("already processed event → processOutbox() skips it")
    void processOutbox_skipsProcessedEvents() {
        OutboxEvent alreadyProcessed = OutboxEvent.builder()
                .aggregateId("user-1")
                .eventType("USER_REGISTERED")
                .payload("{}")
                .processed(true)
                .processedAt(Instant.now())
                .build();
        outboxEventRepository.saveAndFlush(alreadyProcessed);

        assertDoesNotThrow(() -> outboxProcessor.processOutbox());
    }

    @Test
    @DisplayName("unknown event type → processOutbox() stops, event stays unprocessed")
    void processOutbox_unknownEventType_stopsProcessing() {
        OutboxEvent unknown = OutboxEvent.builder()
                .aggregateId("order-1")
                .eventType("UNKNOWN_EVENT")
                .payload("{}")
                .build();
        outboxEventRepository.saveAndFlush(unknown);

        assertDoesNotThrow(() -> outboxProcessor.processOutbox());

        OutboxEvent stillUnprocessed = outboxEventRepository.findById(unknown.getId()).orElseThrow();
        assertFalse(stillUnprocessed.isProcessed(), "Unknown event should remain unprocessed");
    }

    @Test
    @DisplayName("cleanup removes old processed events, keeps recent and unprocessed")
    void cleanup_removesOldProcessedEvents() {
        OutboxEvent old = OutboxEvent.builder()
                .aggregateId("old-1")
                .eventType("USER_REGISTERED")
                .payload("{}")
                .processed(true)
                .processedAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .build();
        outboxEventRepository.saveAndFlush(old);

        OutboxEvent recent = OutboxEvent.builder()
                .aggregateId("recent-1")
                .eventType("USER_REGISTERED")
                .payload("{}")
                .processed(true)
                .processedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
        outboxEventRepository.saveAndFlush(recent);

        OutboxEvent unprocessed = OutboxEvent.builder()
                .aggregateId("new-1")
                .eventType("USER_REGISTERED")
                .payload("{}")
                .build();
        outboxEventRepository.saveAndFlush(unprocessed);

        outboxProcessor.cleanupProcessedEvents();

        assertFalse(outboxEventRepository.findById(old.getId()).isPresent(),
                "Old processed event should be deleted");
        assertTrue(outboxEventRepository.findById(recent.getId()).isPresent(),
                "Recent processed event should be kept");
        assertTrue(outboxEventRepository.findById(unprocessed.getId()).isPresent(),
                "Unprocessed event should be kept");
    }

    private Map<org.apache.kafka.common.TopicPartition, Long> getCurrentEndOffsets() {
        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            var partitionInfos = consumer.partitionsFor(USER_REGISTERED_TOPIC);

            if (partitionInfos == null || partitionInfos.isEmpty()) {
                return Map.of(
                        new org.apache.kafka.common.TopicPartition(USER_REGISTERED_TOPIC, 0), 0L
                );
            }

            var partitions = partitionInfos.stream()
                    .map(info -> new org.apache.kafka.common.TopicPartition(info.topic(), info.partition()))
                    .toList();
            return consumer.endOffsets(partitions);
        }
    }

    private List<ConsumerRecord<String, String>> pollKafkaTopicFrom(
            Map<org.apache.kafka.common.TopicPartition, Long> fromOffsets, int expectedCount) {
        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            var partitionInfos = consumer.partitionsFor(USER_REGISTERED_TOPIC);
            var partitions = partitionInfos.stream()
                    .map(info -> new org.apache.kafka.common.TopicPartition(info.topic(), info.partition()))
                    .toList();
            consumer.assign(partitions);

            for (var tp : partitions) {
                Long offset = fromOffsets.get(tp);
                if (offset != null) {
                    consumer.seek(tp, offset);
                } else {
                    consumer.seekToBeginning(List.of(tp));
                }
            }

            List<ConsumerRecord<String, String>> collected = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 15_000;

            while (collected.size() < expectedCount && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                records.forEach(collected::add);
            }
            return collected;
        }
    }

    private KafkaConsumer<String, String> createConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        ));
    }
}