package com.coopledger.core.outbox;

import com.coopledger.shared.TransferCompleted;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventTransport implements EventTransport {
    private final KafkaTemplate<String, String> kafka;
    public KafkaEventTransport(KafkaTemplate<String, String> kafka) { this.kafka = kafka; }

    @Override
    public void send(OutboxEvent event) throws Exception {
        kafka.send(TransferCompleted.TOPIC, event.getAggregateId().toString(), event.getPayload()).get(6, TimeUnit.SECONDS);
    }
}
