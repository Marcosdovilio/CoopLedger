package com.coopledger.core.accounts;

import com.coopledger.core.ledger.LedgerRepository;
import com.coopledger.core.ledger.LedgerView;
import com.coopledger.shared.*;
import com.coopledger.shared.security.Actor;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    public AccountService(AccountRepository accounts, LedgerRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Transactional
    public AccountView create(String memberId, Actor actor) {
        actor.requireOwnerOrOperations(memberId);
        return AccountView.from(accounts.save(new Account(memberId)));
    }

    @Transactional(readOnly = true)
    public BalanceView balance(UUID id, Actor actor) {
        Account account = authorized(id, actor);
        return new BalanceView(id, account.getBalance(), "BRL", account.getStatus());
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerView> statement(UUID id, Actor actor, int page, int size) {
        authorized(id, actor);
        return PageResponse.from(ledger.findByAccountId(id,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))).map(LedgerView::from));
    }

    @Transactional
    public AccountView changeStatus(UUID id, Account.Status status) {
        Account account = accounts.findLockedById(id).orElseThrow(AccountService::notFound);
        account.changeStatus(status);
        return AccountView.from(account);
    }

    private Account authorized(UUID id, Actor actor) {
        Account account = accounts.findById(id).orElseThrow(AccountService::notFound);
        actor.requireOwnerOrOperations(account.getOwnerId());
        return account;
    }
    public static DomainException notFound() { return new DomainException("ACCOUNT_NOT_FOUND", "Conta não encontrada."); }

    public record AccountView(UUID id, String memberId, Account.Status status, String currency) {
        public static AccountView from(Account account) {
            return new AccountView(account.getId(), account.getOwnerId(), account.getStatus(), "BRL");
        }
    }
    public record BalanceView(UUID accountId, BigDecimal availableBalance, String currency, Account.Status status) { }
}
