package com.swann.orders.api;

public record CreateOrderRequest(String customerName, String itemName, int quantity) {
}
