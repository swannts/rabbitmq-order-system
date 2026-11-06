package com.swann.orders.application;

import java.time.Instant;
import java.util.UUID;

public record OrderCreatedEvent(UUID orderId, String customerName, String itemName, int quantity, Instant createdAt) {
}
