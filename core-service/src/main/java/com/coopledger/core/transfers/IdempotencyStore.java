package com.coopledger.core.transfers;

import com.coopledger.shared.DomainException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IdempotencyStore {
    private final JdbcTemplate jdbc;
    public IdempotencyStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // PostgreSQL waits for a concurrent conflicting insert to commit or roll back.
    // This reservation participates in the very same transaction as the financial writes.
    public UUID reserve(String actor, String key, String fingerprint) {
        int inserted = jdbc.update("""
                INSERT INTO idempotency_keys(actor_id, request_key, fingerprint)
                VALUES (?, ?, ?) ON CONFLICT (actor_id, request_key) DO NOTHING
                """, actor, key, fingerprint);
        if (inserted == 1) return null;
        return jdbc.queryForObject("""
                SELECT fingerprint, transfer_id FROM idempotency_keys WHERE actor_id = ? AND request_key = ?
                """, (rs, row) -> {
            if (!fingerprint.equals(rs.getString("fingerprint")))
                throw new DomainException("IDEMPOTENCY_CONFLICT", "Esta chave já foi utilizada com dados diferentes.");
            UUID transfer = rs.getObject("transfer_id", UUID.class);
            if (transfer == null) throw new IllegalStateException("Reserva sem transferência concluída.");
            return transfer;
        }, actor, key);
    }

    public void complete(String actor, String key, UUID transfer) {
        jdbc.update("UPDATE idempotency_keys SET transfer_id = ? WHERE actor_id = ? AND request_key = ?", transfer, actor, key);
    }
}
