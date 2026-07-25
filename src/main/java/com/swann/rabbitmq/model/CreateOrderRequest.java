package com.swann.rabbitmq.model;

public record CreateOrderRequest(String customerName, String itemName, int quantity) {
}
