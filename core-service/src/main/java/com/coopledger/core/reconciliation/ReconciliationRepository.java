package com.coopledger.core.reconciliation;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRepository extends JpaRepository<ReconciliationRun, UUID> { }
