package com.coopledger.operations.transfers;

import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class ConsumerConfiguration {
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        // Never acknowledge a failed record or silently discard it after a bounded retry.
        DefaultErrorHandler handler = new DefaultErrorHandler(new FixedBackOff(1000, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.setClassifications(Map.of(Exception.class, true), true);
        return handler;
    }
}
