package com.swann.orders.application;

import java.time.Instant;
import java.util.UUID;

public record OrderView(UUID orderId, String customerName, String itemName, int quantity, String status, Instant createdAt) {
}
