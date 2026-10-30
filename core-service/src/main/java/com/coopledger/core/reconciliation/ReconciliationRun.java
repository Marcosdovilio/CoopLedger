package com.coopledger.core.reconciliation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {
    @Id private UUID id;
    @Column(nullable = false, length = 100) private String initiatedBy;
    @Column(nullable = false) private Instant startedAt;
    private Instant completedAt;
    @Column(nullable = false) private long accountsChecked;
    @Column(nullable = false) private long divergenceCount;

    protected ReconciliationRun() { }
    public ReconciliationRun(String actor) { id = UUID.randomUUID(); initiatedBy = actor; startedAt = Instant.now(); }
    public void complete(long accounts, long divergences) {
        accountsChecked = accounts; divergenceCount = divergences; completedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public String getInitiatedBy() { return initiatedBy; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getAccountsChecked() { return accountsChecked; }
    public long getDivergenceCount() { return divergenceCount; }
}
