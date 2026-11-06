package com.swann.orders.application;

import java.time.Instant;
import java.util.UUID;

public record OrderLifecycleEvent(String eventType, int schemaVersion, UUID eventId, UUID orderId,
                                 String customerName, String itemName, int quantity, String status,
                                 Instant occurredAt) {}
