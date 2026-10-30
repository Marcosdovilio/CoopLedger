package com.coopledger.core.transfers;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, UUID> { }
