package com.recoverflow.recovery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryActionRepository extends JpaRepository<RecoveryAction, UUID> {
    Optional<RecoveryAction> findByIdempotencyKey(String idempotencyKey);
    List<RecoveryAction> findByRecoveryCaseId(UUID caseId);
}
