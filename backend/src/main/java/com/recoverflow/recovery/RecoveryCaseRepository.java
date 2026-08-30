package com.recoverflow.recovery;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryCaseRepository extends JpaRepository<RecoveryCase, UUID> {
    Optional<RecoveryCase> findByPaymentId(UUID paymentId);
}
