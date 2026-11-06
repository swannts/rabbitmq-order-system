package com.swann.orders.application;

import java.time.Instant;
import java.util.UUID;
import java.net.URI;

public record OrderView(UUID orderId, String customerName, String itemName, int quantity, String status,
                        String inventoryStatus, String paymentStatus, Instant createdAt, URI self) {
}
