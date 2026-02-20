package my.code.auth.kafka;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import my.code.auth.database.entity.OutboxEvent;
import my.code.auth.database.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OutboxEventPublisher}.
 */
@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxEventPublisher publisher;

    @Captor
    private ArgumentCaptor<OutboxEvent> eventCaptor;


    @Test
    @DisplayName("publish → serializes payload and saves OutboxEvent")
    void publishSavesEvent() throws Exception {
        Object payload = new TestPayload("hello");
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"message\":\"hello\"}");

        publisher.publish("user-42", "USER_REGISTERED", payload);

        verify(outboxEventRepository).save(eventCaptor.capture());

        OutboxEvent saved = eventCaptor.getValue();
        assertEquals("user-42", saved.getAggregateId());
        assertEquals("USER_REGISTERED", saved.getEventType());
        assertEquals("{\"message\":\"hello\"}", saved.getPayload());
    }

    @Test
    @DisplayName("publish → serialization failure throws IllegalArgumentException")
    void serializationFailureThrows() throws Exception {
        Object badPayload = new Object();
        when(objectMapper.writeValueAsString(badPayload))
                .thenThrow(new JsonProcessingException("fail") {
                });

        assertThrows(IllegalArgumentException.class,
                () -> publisher.publish("id", "EVENT", badPayload));

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("publish → event fields are set correctly for different types")
    void differentEventTypes() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        publisher.publish("order-99", "ORDER_PLACED", "data");

        verify(outboxEventRepository).save(eventCaptor.capture());
        assertEquals("order-99", eventCaptor.getValue().getAggregateId());
        assertEquals("ORDER_PLACED", eventCaptor.getValue().getEventType());
    }

    record TestPayload(String message) {
    }
}