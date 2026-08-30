package com.recoverflow.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
    List<AuditEvent> findByCorrelationId(UUID correlationId);
}
