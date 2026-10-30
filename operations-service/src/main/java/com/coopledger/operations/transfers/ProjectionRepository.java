package com.coopledger.operations.transfers;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectionRepository extends JpaRepository<TransferProjection, UUID> { }
