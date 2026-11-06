package com.swann.orders.application;

import com.swann.orders.infrastructure.messaging.OrderMessagePublisher;
import com.swann.orders.infrastructure.persistence.OrderEntity;
import com.swann.orders.infrastructure.persistence.OrderRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderOutcomeApplicationService {
    private final OrderRepository orders;
    private final OrderMessagePublisher publisher;

    public OrderOutcomeApplicationService(OrderRepository orders, OrderMessagePublisher publisher) {
        this.orders = orders;
        this.publisher = publisher;
    }

    @Transactional
    public void recordInventory(UUID orderId, String result) {
        if (!"RESERVED".equals(result) && !"REJECTED".equals(result)) throw new IllegalArgumentException("Unsupported inventory outcome");
        apply(orderId, order -> {
            if (order.getInventoryStatus() != null) return false;
            order.applyInventoryOutcome(result);
            return true;
        });
    }

    @Transactional
    public void recordPayment(UUID orderId, String result) {
        if (!"AUTHORIZED".equals(result) && !"DECLINED".equals(result)) throw new IllegalArgumentException("Unsupported payment outcome");
        apply(orderId, order -> {
            if (order.getPaymentStatus() != null) return false;
            order.applyPaymentOutcome(result);
            return true;
        });
    }

    private void apply(UUID orderId, java.util.function.Predicate<OrderEntity> update) {
        OrderEntity order = orders.findWithLockById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for worker outcome"));
        String priorStatus = order.getStatus();
        if (!update.test(order)) return;
        if (!priorStatus.equals(order.getStatus())) {
            String eventType = "CONFIRMED".equals(order.getStatus()) ? "order.confirmed" : "order.rejected";
            publisher.enqueueOrderState(new OrderLifecycleEvent(eventType, 1, UUID.randomUUID(), order.getId(),
                    order.getCustomerName(), order.getItemName(), order.getQuantity(), order.getStatus(), Instant.now()));
        }
    }
}
