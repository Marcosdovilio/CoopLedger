package com.coopledger.core.transfers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferView(UUID id, UUID sourceAccountId, UUID destinationAccountId, BigDecimal amount,
                           String currency, String description, String status, Instant completedAt) {
    public static TransferView from(Transfer transfer) {
        return new TransferView(transfer.getId(), transfer.getSourceAccountId(), transfer.getDestinationAccountId(),
                transfer.getAmount(), "BRL", transfer.getDescription(), transfer.getStatus(), transfer.getCompletedAt());
    }
}
