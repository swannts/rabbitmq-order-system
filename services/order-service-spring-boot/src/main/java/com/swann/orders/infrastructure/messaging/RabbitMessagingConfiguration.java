package com.swann.orders.infrastructure.messaging;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class RabbitMessagingConfiguration {

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange("orders.dlx", true, false);
    }

    @Bean
    Queue ordersDeadLetterQueue() {
        return QueueBuilder.durable("orders.dead.v1").build();
    }

    @Bean
    Binding ordersDeadLetterBinding(@Qualifier("ordersDeadLetterQueue") Queue ordersDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(ordersDeadLetterQueue).to(deadLetterExchange).with("orders.dead.v1");
    }

    @Bean
    Queue ordersOutcomesQueue() {
        return QueueBuilder.durable("orders.outcomes.v1").deadLetterExchange("orders.dlx").deadLetterRoutingKey("orders.dead.v1").build();
    }

    @Bean
    Queue paymentCommandsQueue() {
        return QueueBuilder.durable("payments.commands.v1").deadLetterExchange("orders.dlx").deadLetterRoutingKey("orders.dead.v1").build();
    }

    @Bean
    Queue inventoryCommandsQueue() {
        return QueueBuilder.durable("inventory.commands.v1").deadLetterExchange("orders.dlx").deadLetterRoutingKey("orders.dead.v1").build();
    }

    @Bean
    MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
