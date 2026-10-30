package com.coopledger.core.outbox;

import io.micrometer.core.instrument.*;
import java.util.concurrent.atomic.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {
    private final JdbcTemplate jdbc;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicReference<Double> age = new AtomicReference<>(0.0);
    public OutboxMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        Gauge.builder("coopledger.outbox.pending", pending, AtomicLong::doubleValue).register(registry);
        Gauge.builder("coopledger.outbox.oldest.age", age, value -> value.get()).baseUnit("seconds").register(registry);
    }

    @Scheduled(fixedDelay = 5000)
    public void refresh() {
        jdbc.query("""
                SELECT count(*) AS pending,
                coalesce(extract(epoch FROM (now() - min(created_at))), 0) AS age
                FROM outbox_events WHERE published_at IS NULL
                """, rs -> { pending.set(rs.getLong("pending")); age.set(rs.getDouble("age")); });
    }
}
