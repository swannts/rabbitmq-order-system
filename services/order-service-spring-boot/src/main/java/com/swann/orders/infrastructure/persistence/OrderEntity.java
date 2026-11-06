package com.swann.orders.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
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
    private String inventoryStatus;
    private String paymentStatus;
    private Instant createdAt;
    @Column(nullable = false, unique = true, length = 128)
    private String idempotencyKey;
    @Column(nullable = false, length = 64)
    private String requestFingerprint;

    protected OrderEntity() {
    }

    public OrderEntity(UUID id, String customerName, String itemName, int quantity, String status, Instant createdAt,
                       String idempotencyKey, String requestFingerprint) {
        this.id = id;
        this.customerName = customerName;
        this.itemName = itemName;
        this.quantity = quantity;
        this.status = status;
        this.createdAt = createdAt;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
    }

    public UUID getId() { return id; }
    public String getCustomerName() { return customerName; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public String getInventoryStatus() { return inventoryStatus; }
    public String getPaymentStatus() { return paymentStatus; }
    public Instant getCreatedAt() { return createdAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void applyInventoryOutcome(String outcome) { inventoryStatus = outcome; updateWorkflowStatus(); }
    public void applyPaymentOutcome(String outcome) { paymentStatus = outcome; updateWorkflowStatus(); }
    private void updateWorkflowStatus() {
        if ("REJECTED".equals(inventoryStatus) || "DECLINED".equals(paymentStatus)) status = "REJECTED";
        else if ("RESERVED".equals(inventoryStatus) && "AUTHORIZED".equals(paymentStatus)) status = "CONFIRMED";
    }
}
