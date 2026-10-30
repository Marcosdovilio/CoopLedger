package com.coopledger.core.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerView(UUID id, UUID transferId, LedgerEntry.Type type, BigDecimal amount,
                         String currency, String description, Instant createdAt) {
    public static LedgerView from(LedgerEntry entry) {
        return new LedgerView(entry.getId(), entry.getTransferId(), entry.getEntryType(), entry.getAmount(),
                "BRL", entry.getDescription(), entry.getCreatedAt());
    }
}
