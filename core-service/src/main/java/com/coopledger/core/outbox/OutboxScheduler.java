package com.coopledger.core.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "coopledger.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {
    private final OutboxRelay relay;
    public OutboxScheduler(OutboxRelay relay) { this.relay = relay; }

    @Scheduled(fixedDelayString = "${coopledger.outbox.poll-interval-ms:1000}")
    public void publish() {
        for (int i = 0; i < 20 && !Thread.currentThread().isInterrupted(); i++) {
            if (!relay.publishNext()) break;
        }
    }
}
