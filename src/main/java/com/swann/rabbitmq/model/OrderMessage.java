package com.swann.rabbitmq.model;

import java.time.Instant;
import java.util.UUID;

public record OrderMessage(
        UUID orderId,
        String customerName,
        String itemName,
        int quantity,
        Instant createdAt
) {
}
