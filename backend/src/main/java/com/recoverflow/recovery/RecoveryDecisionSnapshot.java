package com.recoverflow.recovery;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recovery_decision_snapshots")
public class RecoveryDecisionSnapshot {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "observable_snapshot", nullable = false, columnDefinition = "TEXT")
    private String observableSnapshot;

    @Column(name = "ai_assessment_snapshot", columnDefinition = "TEXT")
    private String aiAssessmentSnapshot;

    @Column(name = "candidate_snapshot", nullable = false, columnDefinition = "TEXT")
    private String candidateSnapshot;

    @Column(name = "policy_snapshot", nullable = false, columnDefinition = "TEXT")
    private String policySnapshot;

    @Column(name = "selected_action")
    private String selectedAction;

    @Column(name = "selection_reason")
    private String selectionReason;

    @Column(name = "selected_expected_net_value", precision = 19, scale = 4)
    private BigDecimal selectedExpectedNetValue;

    @Column(name = "selection_timestamp")
    private Instant selectionTimestamp;

    @Column(name = "estimator_version", nullable = false)
    private String estimatorVersion;

    @Column(name = "policy_version", nullable = false)
    private String policyVersion;

    @Column(name = "ai_provider")
    private String aiProvider;

    @Column(name = "ai_model")
    private String aiModel;

    @Column(name = "ev_version", nullable = false)
    private String evVersion;

    @Column(name = "decision_version", nullable = false)
    private String decisionVersion;

    protected RecoveryDecisionSnapshot() {}

    public RecoveryDecisionSnapshot(UUID id, UUID caseId, Instant createdAt,
                                    String observableSnapshot, String aiAssessmentSnapshot,
                                    String candidateSnapshot, String policySnapshot,
                                    String selectedAction, String selectionReason, BigDecimal selectedExpectedNetValue,
                                    Instant selectionTimestamp,
                                    String estimatorVersion, String policyVersion, String aiProvider, String aiModel,
                                    String evVersion, String decisionVersion) {
        this.id = id != null ? id : UUID.randomUUID();
        this.caseId = caseId;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.observableSnapshot = observableSnapshot;
        this.aiAssessmentSnapshot = aiAssessmentSnapshot;
        this.candidateSnapshot = candidateSnapshot;
        this.policySnapshot = policySnapshot;
        this.selectedAction = selectedAction;
        this.selectionReason = selectionReason;
        this.selectedExpectedNetValue = selectedExpectedNetValue;
        this.selectionTimestamp = selectionTimestamp;
        this.estimatorVersion = estimatorVersion != null ? estimatorVersion : "v1";
        this.policyVersion = policyVersion != null ? policyVersion : "v1";
        this.aiProvider = aiProvider;
        this.aiModel = aiModel;
        this.evVersion = evVersion != null ? evVersion : "ev-v1";
        this.decisionVersion = decisionVersion != null ? decisionVersion : "decision-v1";
    }

    public UUID getId() { return id; }
    public UUID getCaseId() { return caseId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getObservableSnapshot() { return observableSnapshot; }
    public String getAiAssessmentSnapshot() { return aiAssessmentSnapshot; }
    public String getCandidateSnapshot() { return candidateSnapshot; }
    public String getPolicySnapshot() { return policySnapshot; }
    public String getSelectedAction() { return selectedAction; }
    public String getSelectionReason() { return selectionReason; }
    public BigDecimal getSelectedExpectedNetValue() { return selectedExpectedNetValue; }
    public Instant getSelectionTimestamp() { return selectionTimestamp; }
    public String getEstimatorVersion() { return estimatorVersion; }
    public String getPolicyVersion() { return policyVersion; }
    public String getAiProvider() { return aiProvider; }
    public String getAiModel() { return aiModel; }
    public String getEvVersion() { return evVersion; }
    public String getDecisionVersion() { return decisionVersion; }

    public void setAiAssessmentSnapshot(String s) { this.aiAssessmentSnapshot = s; }
    public void setCandidateSnapshot(String s) { this.candidateSnapshot = s; }
    public void setPolicySnapshot(String s) { this.policySnapshot = s; }
    public void setSelectedAction(String s) { this.selectedAction = s; }
    public void setSelectionReason(String s) { this.selectionReason = s; }
    public void setSelectedExpectedNetValue(BigDecimal v) { this.selectedExpectedNetValue = v; }
    public void setSelectionTimestamp(Instant t) { this.selectionTimestamp = t; }
}
