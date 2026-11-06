package com.swann.orders.infrastructure.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMessagingConfiguration {

    @Bean
    Queue paymentCommandsQueue() {
        return QueueBuilder.durable("payments.commands").build();
    }

    @Bean
    Queue inventoryCommandsQueue() {
        return QueueBuilder.durable("inventory.commands").build();
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
