package com.coopledger.core.accounts;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findLockedById(UUID id);
}
