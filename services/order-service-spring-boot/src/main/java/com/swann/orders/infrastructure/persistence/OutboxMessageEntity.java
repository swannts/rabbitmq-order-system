package com.swann.orders.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_messages")
public class OutboxMessageEntity {
    @Id
    private UUID id;
    @Column(nullable = false, length = 64)
    private String eventType;
    @Column(nullable = false, length = 128)
    private String aggregateId;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant publishedAt;
    @Column(nullable = false)
    private int attempts;
    @Column(length = 1000)
    private String lastError;

    protected OutboxMessageEntity() {}

    public OutboxMessageEntity(UUID id, String eventType, String aggregateId, String payload, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.createdAt = createdAt;
        this.attempts = 0;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public void markPublished(Instant at) { publishedAt = at; lastError = null; }
    public void markFailed(String message) { attempts++; lastError = message.length() > 1000 ? message.substring(0, 1000) : message; }
}
