package com.coopledger.core.reconciliation;

import com.coopledger.shared.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ReconciliationService {
    private final ReconciliationRepository runs;
    private final JdbcTemplate jdbc;
    public ReconciliationService(ReconciliationRepository runs, JdbcTemplate jdbc) { this.runs = runs; this.jdbc = jdbc; }

    // Both counters and the aggregation use the same MVCC snapshot, even during transfers.
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public RunView execute(String actor) {
        ReconciliationRun run = runs.saveAndFlush(new ReconciliationRun(actor));
        long accounts = jdbc.queryForObject("SELECT count(*) FROM accounts", Long.class);
        long divergences = jdbc.update("""
                INSERT INTO reconciliation_discrepancies(run_id, account_id, stored_balance, ledger_balance, difference)
                SELECT ?, a.id, a.balance, coalesce(sum(
                    CASE WHEN l.entry_type = 'DEBIT' THEN -l.amount ELSE l.amount END), 0),
                    a.balance - coalesce(sum(CASE WHEN l.entry_type = 'DEBIT' THEN -l.amount ELSE l.amount END), 0)
                FROM accounts a LEFT JOIN ledger_entries l ON l.account_id = a.id
                GROUP BY a.id, a.balance
                HAVING a.balance <> coalesce(sum(CASE WHEN l.entry_type = 'DEBIT' THEN -l.amount ELSE l.amount END), 0)
                """, run.getId());
        run.complete(accounts, divergences);
        return RunView.from(run);
    }

    @Transactional(readOnly = true)
    public RunView find(UUID id) {
        return RunView.from(runs.findById(id).orElseThrow(() -> new DomainException("RUN_NOT_FOUND", "Conciliação não encontrada.")));
    }

    @Transactional(readOnly = true)
    public PageResponse<Divergence> divergences(UUID id, int page, int size) {
        find(id);
        long count = jdbc.queryForObject("SELECT count(*) FROM reconciliation_discrepancies WHERE run_id = ?", Long.class, id);
        List<Divergence> rows = jdbc.query("""
                SELECT account_id, stored_balance, ledger_balance, difference
                FROM reconciliation_discrepancies WHERE run_id = ? ORDER BY account_id LIMIT ? OFFSET ?
                """, (rs, index) -> new Divergence(rs.getObject("account_id", UUID.class), rs.getBigDecimal("stored_balance"),
                rs.getBigDecimal("ledger_balance"), rs.getBigDecimal("difference")), id, size, (long) page * size);
        return PageResponse.from(new PageImpl<>(rows, PageRequest.of(page, size), count));
    }

    public record RunView(UUID id, String status, String initiatedBy, Instant startedAt, Instant completedAt,
                          long accountsChecked, long divergenceCount) {
        static RunView from(ReconciliationRun run) {
            return new RunView(run.getId(), "COMPLETED", run.getInitiatedBy(), run.getStartedAt(), run.getCompletedAt(),
                    run.getAccountsChecked(), run.getDivergenceCount());
        }
    }
    public record Divergence(UUID accountId, BigDecimal storedBalance, BigDecimal ledgerBalance, BigDecimal difference) { }
}
