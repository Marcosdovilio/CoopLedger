package com.coopledger.core.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private UUID aggregateId;
    @Column(nullable = false, updatable = false, columnDefinition = "text") private String payload;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private Instant nextAttemptAt;
    private Instant publishedAt;
    @Column(length = 500) private String lastError;

    protected OutboxEvent() { }
    public OutboxEvent(UUID id, UUID transferId, String payload, Instant at) {
        this.id = id; this.aggregateId = transferId; this.payload = payload; this.createdAt = at; this.nextAttemptAt = at;
    }

    public void published() { attempts++; publishedAt = Instant.now(); lastError = null; }
    public void failed(Exception exception) {
        attempts++;
        long delay = Math.min(300, 1L << Math.min(attempts, 8));
        nextAttemptAt = Instant.now().plusSeconds(delay);
        String message = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        lastError = message.substring(0, Math.min(500, message.length()));
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
}
