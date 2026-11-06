package com.swann.orders.infrastructure.messaging;

import com.swann.orders.application.OrderCreatedEvent;
import java.util.Map;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderMessagePublisher {

    private final RabbitTemplate rabbit;
    private final KafkaTemplate<String, Object> kafka;
    private final String orderEventsTopic;

    public OrderMessagePublisher(
            RabbitTemplate rabbit,
            KafkaTemplate<String, Object> kafka,
            @Value("${app.messaging.order-events-topic}") String orderEventsTopic
    ) {
        this.rabbit = rabbit;
        this.kafka = kafka;
        this.orderEventsTopic = orderEventsTopic;
    }

    public void requestPayment(OrderCreatedEvent event) {
        rabbit.convertAndSend("payments.commands", Map.of("pattern", "payments.authorize", "data", event));
    }

    public void requestInventory(OrderCreatedEvent event) {
        rabbit.convertAndSend("inventory.commands", Map.of("pattern", "inventory.reserve", "data", event));
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        kafka.send(orderEventsTopic, event.orderId().toString(), event);
    }
}
