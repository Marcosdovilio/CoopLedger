package com.coopledger.operations.transfers;

import com.coopledger.shared.TransferCompleted;
import jakarta.validation.Validator;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectionService {
    private final JdbcTemplate jdbc;
    private final Validator validator;
    public ProjectionService(JdbcTemplate jdbc, Validator validator) { this.jdbc = jdbc; this.validator = validator; }

    @Transactional
    public boolean apply(TransferCompleted event) {
        if (!validator.validate(event).isEmpty() || !TransferCompleted.TYPE.equals(event.eventType())
                || event.sourceAccountId().equals(event.destinationAccountId()))
            throw new IllegalArgumentException("Contrato de evento inválido ou não suportado.");
        int inserted = jdbc.update("""
                INSERT INTO processed_events(event_id, processed_at) VALUES (?, now())
                ON CONFLICT (event_id) DO NOTHING
                """, event.eventId());
        if (inserted == 0) return false;
        // A database error rolls back BOTH the deduplication marker and this projection.
        jdbc.update("""
                INSERT INTO transfer_views(transfer_id, event_id, source_account_id, destination_account_id,
                    amount, description, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, event.transferId(), event.eventId(), event.sourceAccountId(), event.destinationAccountId(),
                event.amount(), event.description(), Timestamp.from(event.occurredAt()));
        return true;
    }
}
