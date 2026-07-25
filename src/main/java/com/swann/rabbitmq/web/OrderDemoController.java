package com.swann.rabbitmq.web;

import com.swann.rabbitmq.model.CreateOrderRequest;
import com.swann.rabbitmq.model.OrderMessage;
import com.swann.rabbitmq.service.OrderMessageConsumer;
import com.swann.rabbitmq.service.OrderMessagePublisher;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderDemoController {

    private final OrderMessagePublisher publisher;
    private final OrderMessageConsumer consumer;

    public OrderDemoController(OrderMessagePublisher publisher, OrderMessageConsumer consumer) {
        this.publisher = publisher;
        this.consumer = consumer;
    }

    @PostMapping
    public OrderMessage createOrder(@RequestBody CreateOrderRequest request) {
        OrderMessage message = new OrderMessage(
                UUID.randomUUID(),
                request.customerName(),
                request.itemName(),
                request.quantity(),
                Instant.now()
        );
        return publisher.publish(message);
    }

    @GetMapping("/processed")
    public List<OrderMessage> processedOrders() {
        return consumer.getProcessedOrders();
    }
}
