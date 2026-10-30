package com.coopledger.shared;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferCompleted(
        @NotNull UUID eventId, @NotBlank String eventType, @Min(1) @Max(1) int schemaVersion,
        @NotNull Instant occurredAt, @NotNull UUID transferId,
        @NotNull UUID sourceAccountId, @NotNull UUID destinationAccountId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotNull @Size(max = 255) String description) {
    public static final String TYPE = "TransferCompleted";
    public static final String TOPIC = "coopledger.transfers.completed.v1";
}
