package com.coopledger.core.accounts;

import com.coopledger.shared.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    public enum Status { ACTIVE, BLOCKED }

    @Id private UUID id;
    @Column(nullable = false, updatable = false, length = 100) private String ownerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal balance;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    protected Account() { }
    public Account(String ownerId) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.status = Status.ACTIVE;
        this.balance = new BigDecimal("0.00");
        this.createdAt = Instant.now();
    }

    public void requireActive() {
        if (status != Status.ACTIVE) throw new DomainException("ACCOUNT_BLOCKED", "Uma das contas está bloqueada.");
    }

    public void debit(BigDecimal value) {
        requireActive();
        value = Money.positive(value);
        if (balance.compareTo(value) < 0) throw new DomainException("INSUFFICIENT_FUNDS", "Saldo insuficiente.");
        balance = balance.subtract(value);
    }

    public void credit(BigDecimal value) {
        requireActive();
        value = Money.positive(value);
        if (balance.add(value).compareTo(Money.MAX) > 0) throw new DomainException("BALANCE_LIMIT", "Limite de saldo da conta excedido.");
        balance = balance.add(value);
    }

    public UUID getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public Status getStatus() { return status; }
    public BigDecimal getBalance() { return balance; }
    public Instant getCreatedAt() { return createdAt; }
    public void changeStatus(Status status) { this.status = status; }
}
