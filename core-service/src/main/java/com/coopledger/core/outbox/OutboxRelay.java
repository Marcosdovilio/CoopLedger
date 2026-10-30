package com.coopledger.core.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private final OutboxRepository events;
    private final EventTransport transport;
    private final MeterRegistry metrics;
    public OutboxRelay(OutboxRepository events, EventTransport transport, MeterRegistry metrics) {
        this.events = events; this.transport = transport; this.metrics = metrics;
    }

    @Transactional
    public boolean publishNext() {
        var candidate = events.lockNext();
        if (candidate.isEmpty()) return false;
        OutboxEvent event = candidate.get();
        try {
            transport.send(event); // Only mark published after the broker acknowledges.
            event.published();
            metrics.counter("coopledger.outbox.publications", "result", "published").increment();
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            event.failed(ex);
            metrics.counter("coopledger.outbox.publications", "result", "failed").increment();
            log.warn("Evento {} permanece na outbox: {}", event.getId(), ex.toString());
        }
        return true;
    }
}
