package com.coopledger.operations.transfers;

import com.coopledger.shared.TransferCompleted;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransferEventListener {
    private final ObjectMapper mapper;
    private final ProjectionService projections;
    private final MeterRegistry metrics;
    public TransferEventListener(ObjectMapper mapper, ProjectionService projections, MeterRegistry metrics) {
        this.mapper = mapper; this.projections = projections; this.metrics = metrics;
    }

    @KafkaListener(topics = TransferCompleted.TOPIC)
    public void consume(String payload) throws JsonProcessingException {
        TransferCompleted event = mapper.readValue(payload, TransferCompleted.class);
        boolean applied = projections.apply(event); // The DB transaction has committed before returning.
        metrics.counter("coopledger.operations.events", "result", applied ? "applied" : "duplicate").increment();
    }
}
