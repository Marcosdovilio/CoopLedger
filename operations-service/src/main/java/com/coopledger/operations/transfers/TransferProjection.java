package com.coopledger.operations.transfers;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer_views")
public class TransferProjection {
    @Id private UUID transferId;
    @Column(nullable = false) private UUID eventId;
    @Column(nullable = false) private UUID sourceAccountId;
    @Column(nullable = false) private UUID destinationAccountId;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 255) private String description;
    @Column(nullable = false) private Instant completedAt;

    protected TransferProjection() { }
    public UUID getTransferId() { return transferId; }
    public UUID getEventId() { return eventId; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public Instant getCompletedAt() { return completedAt; }
    public String getCurrency() { return "BRL"; }
    public String getStatus() { return "COMPLETED"; }
}
