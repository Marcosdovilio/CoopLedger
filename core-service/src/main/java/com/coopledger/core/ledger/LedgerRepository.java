package com.coopledger.core.ledger;

import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerRepository extends JpaRepository<LedgerEntry, UUID> {
    Page<LedgerEntry> findByAccountId(UUID accountId, Pageable pageable);
}
