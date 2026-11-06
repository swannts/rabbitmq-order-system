package com.swann.orders.application;

import com.swann.orders.api.CreateOrderRequest;
import com.swann.orders.infrastructure.messaging.OrderMessagePublisher;
import com.swann.orders.infrastructure.persistence.OrderEntity;
import com.swann.orders.infrastructure.persistence.OrderRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderApplicationService {

    private final OrderRepository orders;
    private final OrderMessagePublisher publisher;

    public OrderApplicationService(OrderRepository orders, OrderMessagePublisher publisher) {
        this.orders = orders;
        this.publisher = publisher;
    }

    @Transactional
    public OrderView create(CreateOrderRequest request) {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        OrderEntity order = orders.save(new OrderEntity(id, request.customerName(), request.itemName(), request.quantity(), "PENDING", createdAt));
        OrderCreatedEvent event = new OrderCreatedEvent(id, request.customerName(), request.itemName(), request.quantity(), createdAt);

        publisher.requestPayment(event);
        publisher.requestInventory(event);
        publisher.publishOrderCreated(event);

        return new OrderView(order.getId(), order.getCustomerName(), order.getItemName(), order.getQuantity(), order.getStatus(), order.getCreatedAt());
    }
}
