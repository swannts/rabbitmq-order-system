package com.swann.orders.infrastructure.messaging;

import com.swann.orders.application.OrderOutcomeApplicationService;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

@Component
public class OrderOutcomeListener {
    private final ObjectMapper mapper;
    private final OrderOutcomeApplicationService outcomes;

    public OrderOutcomeListener(ObjectMapper mapper, OrderOutcomeApplicationService outcomes) {
        this.mapper = mapper;
        this.outcomes = outcomes;
    }

    @RabbitListener(queues = "orders.outcomes.v1")
    public void receive(Message message) throws Exception {
        JsonNode packet = mapper.readTree(message.getBody());
        String pattern = packet.path("pattern").asString();
        JsonNode data = packet.path("data");
        UUID orderId = UUID.fromString(data.path("orderId").asString());
        String status = data.path("status").asString();
        switch (pattern) {
            case "orders.inventory-result" -> outcomes.recordInventory(orderId, status);
            case "orders.payment-result" -> outcomes.recordPayment(orderId, status);
            default -> throw new IllegalArgumentException("Unsupported outcome pattern: " + pattern);
        }
    }
}
