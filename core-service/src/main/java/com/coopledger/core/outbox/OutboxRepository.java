package com.coopledger.core.outbox;

import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE published_at IS NULL AND next_attempt_at <= now()
            ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<OutboxEvent> lockNext();
}
