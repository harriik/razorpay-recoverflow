package com.recoverflow.evaluation;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, UUID> {}
