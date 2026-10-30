package com.coopledger.core.outbox;

import com.coopledger.core.transfers.Transfer;
import com.coopledger.shared.TransferCompleted;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OutboxWriter {
    private final OutboxRepository events;
    private final ObjectMapper mapper;
    public OutboxWriter(OutboxRepository events, ObjectMapper mapper) { this.events = events; this.mapper = mapper; }

    public void append(Transfer transfer) {
        UUID eventId = UUID.randomUUID();
        var event = new TransferCompleted(eventId, TransferCompleted.TYPE, 1, transfer.getCompletedAt(),
                transfer.getId(), transfer.getSourceAccountId(), transfer.getDestinationAccountId(),
                transfer.getAmount(), transfer.getDescription());
        try {
            events.save(new OutboxEvent(eventId, transfer.getId(), mapper.writeValueAsString(event), transfer.getCompletedAt()));
        } catch (JsonProcessingException ex) { throw new IllegalStateException("Falha ao serializar evento.", ex); }
    }
}
