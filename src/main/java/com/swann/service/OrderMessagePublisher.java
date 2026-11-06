package com.swann.rabbitmq.service;

import com.swann.rabbitmq.config.RabbitMqConfig;
import com.swann.rabbitmq.model.dto.OrderMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public OrderMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public OrderMessage publish(OrderMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.ORDER_EXCHANGE,
                RabbitMqConfig.ORDER_ROUTING_KEY,
                message
        );
        return message;
    }
}
