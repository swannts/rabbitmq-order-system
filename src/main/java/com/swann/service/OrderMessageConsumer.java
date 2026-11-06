package com.swann.rabbitmq.service;

import com.swann.rabbitmq.config.RabbitMqConfig;
import com.swann.rabbitmq.model.dto.OrderMessage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderMessageConsumer.class);

    private final List<OrderMessage> processedOrders = new CopyOnWriteArrayList<>();

    @RabbitListener(queues = RabbitMqConfig.ORDER_QUEUE)
    public void handleOrderCreated(OrderMessage message) {
        processedOrders.add(message);
        log.info("Processed order message: {}", message);
    }

    public List<OrderMessage> getProcessedOrders() {
        return List.copyOf(processedOrders);
    }
}
