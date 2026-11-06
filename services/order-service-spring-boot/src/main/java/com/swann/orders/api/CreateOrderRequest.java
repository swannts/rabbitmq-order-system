package com.swann.orders.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotBlank @Size(max = 160) String customerName,
        @NotBlank @Size(max = 160) String itemName,
        @Positive int quantity
) {
}
