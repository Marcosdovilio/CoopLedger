package com.coopledger.core.transfers;

import com.coopledger.core.accounts.*;
import com.coopledger.core.outbox.OutboxWriter;
import com.coopledger.shared.*;
import com.coopledger.shared.security.Actor;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferExecutor {
    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final IdempotencyStore keys;
    private final TransferPosting posting;
    private final OutboxWriter outbox;

    public TransferExecutor(AccountRepository accounts, TransferRepository transfers, IdempotencyStore keys,
                            TransferPosting posting, OutboxWriter outbox) {
        this.accounts = accounts; this.transfers = transfers; this.keys = keys; this.posting = posting; this.outbox = outbox;
    }

    @Transactional(timeout = 15)
    public Result execute(TransferRequest request, String key, Actor actor) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}"))
            throw new DomainException("INVALID_IDEMPOTENCY_KEY", "Informe Idempotency-Key com 1 a 128 caracteres ASCII alfanuméricos ou . _ : -.");
        var amount = Money.positive(request.amount());
        if (request.sourceAccountId().equals(request.destinationAccountId()))
            throw new DomainException("SAME_ACCOUNT", "As contas de origem e destino devem ser diferentes.");
        String description = request.description() == null ? "" : request.description();
        String canonical = request.sourceAccountId() + "\n" + request.destinationAccountId() + "\n" + amount.toPlainString() + "\n" + description;
        UUID existing = keys.reserve(actor.id(), key, fingerprint(canonical));
        if (existing != null) {
            Transfer transfer = transfers.findById(existing).orElseThrow();
            actor.requireOwnerOrOperations(accounts.findById(transfer.getSourceAccountId())
                    .orElseThrow(AccountService::notFound).getOwnerId());
            return new Result(TransferView.from(transfer), true);
        }

        // Separate ordered SELECT FOR UPDATE statements make the acquisition order explicit.
        List<UUID> ids = new ArrayList<>(List.of(request.sourceAccountId(), request.destinationAccountId()));
        ids.sort(Comparator.comparing(UUID::toString));
        Map<UUID, Account> locked = new HashMap<>();
        for (UUID id : ids) locked.put(id, accounts.findLockedById(id).orElseThrow(AccountService::notFound));
        Account source = locked.get(request.sourceAccountId());
        Account destination = locked.get(request.destinationAccountId());
        actor.requireOwnerOrOperations(source.getOwnerId());
        source.requireActive();
        destination.requireActive();
        Transfer transfer = transfers.saveAndFlush(new Transfer(source.getId(), destination.getId(), actor.id(), amount, description));
        posting.debit(source, transfer);
        posting.credit(destination, transfer);
        keys.complete(actor.id(), key, transfer.getId());
        outbox.append(transfer);
        return new Result(TransferView.from(transfer), false);
    }

    private String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    public record Result(TransferView transfer, boolean replayed) { }
}
