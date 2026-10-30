package com.coopledger.core.ledger;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
    public enum Type { DEBIT, CREDIT, OPENING_CREDIT }
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private UUID accountId;
    @Column(updatable = false) private UUID transferId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 20) private Type entryType;
    @Column(nullable = false, updatable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, updatable = false, length = 255) private String description;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    protected LedgerEntry() { }
    public LedgerEntry(UUID accountId, UUID transferId, Type entryType, BigDecimal amount, String description, Instant at) {
        id = UUID.randomUUID();
        this.accountId = accountId;
        this.transferId = transferId;
        this.entryType = entryType;
        this.amount = amount;
        this.description = description;
        createdAt = at;
    }
    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public UUID getTransferId() { return transferId; }
    public Type getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
