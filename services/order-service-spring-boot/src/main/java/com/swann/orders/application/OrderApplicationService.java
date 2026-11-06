package com.swann.orders.application;

import com.swann.orders.api.CreateOrderRequest;
import com.swann.orders.infrastructure.messaging.OrderMessagePublisher;
import com.swann.orders.infrastructure.persistence.OrderEntity;
import com.swann.orders.infrastructure.persistence.OrderRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderApplicationService {

    private final OrderRepository orders;
    private final OrderMessagePublisher publisher;

    public OrderApplicationService(OrderRepository orders, OrderMessagePublisher publisher) {
        this.orders = orders;
        this.publisher = publisher;
    }

    @Transactional
    public OrderView create(CreateOrderRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must contain 1 to 128 characters");
        }
        idempotencyKey = idempotencyKey.trim();
        String fingerprint = fingerprint(request);
        var prior = orders.findByIdempotencyKey(idempotencyKey);
        if (prior.isPresent()) {
            if (!prior.get().getRequestFingerprint().equals(fingerprint)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was already used with a different request");
            }
            return view(prior.get());
        }

        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        String customerName = request.customerName().trim();
        String itemName = request.itemName().trim();
        OrderEntity order = orders.save(new OrderEntity(id, customerName, itemName, request.quantity(), "PENDING", createdAt, idempotencyKey, fingerprint));
        publisher.enqueueOrderWorkflow(new OrderCreatedEvent("order.created", 1, UUID.randomUUID(), id, customerName, itemName, request.quantity(), "PENDING", createdAt));
        return view(order);
    }

    @Transactional(readOnly = true)
    public OrderView get(UUID id) {
        return view(orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")));
    }

    @Transactional(readOnly = true)
    public Page<OrderView> list(Pageable pageable) {
        return orders.findAllByOrderByCreatedAtDesc(pageable).map(this::view);
    }

    private OrderView view(OrderEntity order) {
        return new OrderView(order.getId(), order.getCustomerName(), order.getItemName(), order.getQuantity(), order.getStatus(),
                order.getInventoryStatus(), order.getPaymentStatus(), order.getCreatedAt(),
                java.net.URI.create("/api/v1/orders/" + order.getId()));
    }

    private String fingerprint(CreateOrderRequest request) {
        try {
            String canonical = request.customerName().trim() + "\n" + request.itemName().trim() + "\n" + request.quantity();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
