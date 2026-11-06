package com.swann.orders.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity {

    @Id
    private UUID id;
    private String customerName;
    private String itemName;
    private int quantity;
    private String status;
    private Instant createdAt;

    protected OrderEntity() {
    }

    public OrderEntity(UUID id, String customerName, String itemName, int quantity, String status, Instant createdAt) {
        this.id = id;
        this.customerName = customerName;
        this.itemName = itemName;
        this.quantity = quantity;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getCustomerName() { return customerName; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
