package com.coopledger.core.transfers;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private UUID sourceAccountId;
    @Column(nullable = false, updatable = false) private UUID destinationAccountId;
    @Column(nullable = false, updatable = false, length = 100) private String actorId;
    @Column(nullable = false, updatable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, updatable = false, length = 255) private String description;
    @Column(nullable = false, updatable = false, length = 20) private String status;
    @Column(nullable = false, updatable = false) private Instant completedAt;

    protected Transfer() { }
    public Transfer(UUID source, UUID destination, String actor, BigDecimal amount, String description) {
        id = UUID.randomUUID();
        sourceAccountId = source;
        destinationAccountId = destination;
        actorId = actor;
        this.amount = amount;
        this.description = description;
        status = "COMPLETED";
        completedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
    public UUID getId() { return id; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public String getActorId() { return actorId; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public Instant getCompletedAt() { return completedAt; }
}
