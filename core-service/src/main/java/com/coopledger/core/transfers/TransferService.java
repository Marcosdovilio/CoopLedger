package com.coopledger.core.transfers;

import com.coopledger.core.accounts.*;
import com.coopledger.shared.DomainException;
import com.coopledger.shared.security.Actor;
import io.micrometer.core.instrument.*;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {
    private final TransferExecutor executor;
    private final TransferRepository transfers;
    private final AccountRepository accounts;
    private final MeterRegistry metrics;

    public TransferService(TransferExecutor executor, TransferRepository transfers, AccountRepository accounts, MeterRegistry metrics) {
        this.executor = executor; this.transfers = transfers; this.accounts = accounts; this.metrics = metrics;
    }

    public TransferExecutor.Result transfer(TransferRequest request, String key, Actor actor) {
        Timer.Sample timer = Timer.start(metrics);
        String result = "error";
        try {
            var response = executor.execute(request, key, actor); // returns only after the transaction commits
            result = response.replayed() ? "replayed" : "completed";
            return response;
        } catch (DomainException | AccessDeniedException ex) {
            result = "rejected";
            throw ex;
        } finally {
            metrics.counter("coopledger.transfers", "result", result).increment();
            timer.stop(metrics.timer("coopledger.transfer.duration", "result", result));
        }
    }

    @Transactional(readOnly = true)
    public TransferView find(UUID id, Actor actor) {
        Transfer transfer = transfers.findById(id)
                .orElseThrow(() -> new DomainException("TRANSFER_NOT_FOUND", "Transferência não encontrada."));
        boolean participant = accounts.findById(transfer.getSourceAccountId()).orElseThrow().getOwnerId().equals(actor.id())
                || accounts.findById(transfer.getDestinationAccountId()).orElseThrow().getOwnerId().equals(actor.id());
        if (!actor.operations() && !participant) throw new AccessDeniedException("Acesso à transferência não autorizado.");
        return TransferView.from(transfer);
    }
}
