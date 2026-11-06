package com.swann.orders.infrastructure.messaging;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.swann.orders.application.OrderCreatedEvent;
import com.swann.orders.infrastructure.persistence.OutboxMessageEntity;
import com.swann.orders.infrastructure.persistence.OutboxMessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OrderMessagePublisher {
    private static final Logger log = LoggerFactory.getLogger(OrderMessagePublisher.class);
    private final OutboxMessageRepository outbox;
    private final ObjectMapper mapper;
    private final RabbitTemplate rabbit;
    private final KafkaTemplate<String, String> kafka;
    private final String orderEventsTopic;

    public OrderMessagePublisher(OutboxMessageRepository outbox, ObjectMapper mapper, RabbitTemplate rabbit,
                                 KafkaTemplate<String, String> kafka,
                                 @Value("${app.messaging.order-events-topic}") String orderEventsTopic) {
        this.outbox = outbox;
        this.mapper = mapper;
        this.rabbit = rabbit;
        this.kafka = kafka;
        this.orderEventsTopic = orderEventsTopic;
    }

    @Transactional
    public void enqueueOrderWorkflow(OrderCreatedEvent event) {
        String payload = serialize(event);
        outbox.saveAll(List.of(
                new OutboxMessageEntity(UUID.randomUUID(), "payment.authorize", event.orderId().toString(), payload, event.occurredAt()),
                new OutboxMessageEntity(UUID.randomUUID(), "inventory.reserve", event.orderId().toString(), payload, event.occurredAt()),
                new OutboxMessageEntity(UUID.randomUUID(), "order.created", event.orderId().toString(), payload, event.occurredAt())
        ));
    }

    @Transactional
    public void enqueueOrderState(com.swann.orders.application.OrderLifecycleEvent event) {
        outbox.save(new OutboxMessageEntity(UUID.randomUUID(), event.eventType(), event.orderId().toString(), serialize(event), event.occurredAt()));
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:1000}")
    public void publishPending() {
        List<OutboxMessageEntity> pending = outbox.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 50));
        for (OutboxMessageEntity message : pending) {
            try {
                publish(message);
                message.markPublished(Instant.now());
                outbox.save(message);
            } catch (Exception exception) {
                message.markFailed(exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
                outbox.save(message);
                log.error("Outbox delivery failed id={} type={} attempts={}", message.getId(), message.getEventType(), message.getAttempts(), exception);
            }
        }
    }

    private void publish(OutboxMessageEntity message) throws Exception {
        var payload = mapper.readTree(message.getPayload());
        switch (message.getEventType()) {
            case "payment.authorize" -> publishRabbit("payments.commands.v1", java.util.Map.of("pattern", "payments.authorize", "data", payload));
            case "inventory.reserve" -> publishRabbit("inventory.commands.v1", java.util.Map.of("pattern", "inventory.reserve", "data", payload));
            case "order.created", "order.confirmed", "order.rejected" -> kafka.send(orderEventsTopic, message.getAggregateId(), message.getPayload()).get(10, java.util.concurrent.TimeUnit.SECONDS);
            default -> throw new IllegalArgumentException("Unknown outbox event type: " + message.getEventType());
        }
    }

    private void publishRabbit(String queue, Object body) throws Exception {
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        rabbit.convertAndSend(queue, body, correlation);
        var confirm = correlation.getFuture().get(10, java.util.concurrent.TimeUnit.SECONDS);
        if (!confirm.isAck() || correlation.getReturned() != null) {
            throw new IllegalStateException("RabbitMQ did not confirm message delivery to " + queue + ": " + confirm.getReason());
        }
    }

    private String serialize(Object event) {
        try {
            return mapper.writeValueAsString(event);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize order event", exception);
        }
    }
}
