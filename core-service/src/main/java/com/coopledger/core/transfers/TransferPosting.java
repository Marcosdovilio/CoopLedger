package com.coopledger.core.transfers;

import com.coopledger.core.accounts.Account;
import com.coopledger.core.ledger.*;
import org.springframework.stereotype.Component;

@Component
public class TransferPosting {
    private final LedgerRepository ledger;
    public TransferPosting(LedgerRepository ledger) { this.ledger = ledger; }

    public void debit(Account source, Transfer transfer) {
        source.debit(transfer.getAmount());
        append(source, transfer, LedgerEntry.Type.DEBIT);
    }

    public void credit(Account destination, Transfer transfer) {
        destination.credit(transfer.getAmount());
        append(destination, transfer, LedgerEntry.Type.CREDIT);
    }

    private void append(Account account, Transfer transfer, LedgerEntry.Type type) {
        ledger.saveAndFlush(new LedgerEntry(account.getId(), transfer.getId(), type, transfer.getAmount(),
                transfer.getDescription(), transfer.getCompletedAt()));
    }
}
