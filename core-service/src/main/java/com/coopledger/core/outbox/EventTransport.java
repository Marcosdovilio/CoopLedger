package com.coopledger.core.outbox;

public interface EventTransport {
    void send(OutboxEvent event) throws Exception;
}
