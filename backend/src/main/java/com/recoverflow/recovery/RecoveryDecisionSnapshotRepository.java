package com.recoverflow.recovery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryDecisionSnapshotRepository extends JpaRepository<RecoveryDecisionSnapshot, UUID> {
    Optional<RecoveryDecisionSnapshot> findTopByCaseIdOrderByCreatedAtDesc(UUID caseId);
    List<RecoveryDecisionSnapshot> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
    List<RecoveryDecisionSnapshot> findByCaseId(UUID caseId);
}
