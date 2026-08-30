package com.recoverflow.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evaluation_runs")
public class EvaluationRun {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "seed", nullable = false)
    private Long seed;

    @Column(name = "dataset_size", nullable = false)
    private Integer datasetSize;

    @Column(name = "estimator_version", nullable = false)
    private String estimatorVersion;

    @Column(name = "policy_version", nullable = false)
    private String policyVersion;

    @Column(name = "synthetic_registry_version", nullable = false)
    private String syntheticRegistryVersion;

    @Column(name = "config_snapshot", columnDefinition = "TEXT")
    private String configSnapshot;

    @Column(name = "baseline_a_result", columnDefinition = "TEXT")
    private String baselineAResult;

    @Column(name = "baseline_b_result", columnDefinition = "TEXT")
    private String baselineBResult;

    @Column(name = "recoverflow_result", columnDefinition = "TEXT")
    private String recoverflowResult;

    @Column(name = "ablation_summary", columnDefinition = "TEXT")
    private String ablationSummary;

    @Column(name = "incremental_revenue_a", precision = 19, scale = 4)
    private BigDecimal incrementalRevenueA;

    @Column(name = "incremental_revenue_b", precision = 19, scale = 4)
    private BigDecimal incrementalRevenueB;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EvaluationRun() {}

    public EvaluationRun(UUID id, long seed, int datasetSize, String estimatorVersion, String policyVersion, String syntheticRegistryVersion) {
        this.id = id != null ? id : UUID.randomUUID();
        this.seed = seed;
        this.datasetSize = datasetSize;
        this.estimatorVersion = estimatorVersion;
        this.policyVersion = policyVersion;
        this.syntheticRegistryVersion = syntheticRegistryVersion;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Long getSeed() { return seed; }
    public Integer getDatasetSize() { return datasetSize; }
    public String getEstimatorVersion() { return estimatorVersion; }
    public String getPolicyVersion() { return policyVersion; }
    public String getSyntheticRegistryVersion() { return syntheticRegistryVersion; }
    public String getConfigSnapshot() { return configSnapshot; }
    public String getBaselineAResult() { return baselineAResult; }
    public String getBaselineBResult() { return baselineBResult; }
    public String getRecoverflowResult() { return recoverflowResult; }
    public String getAblationSummary() { return ablationSummary; }
    public BigDecimal getIncrementalRevenueA() { return incrementalRevenueA; }
    public BigDecimal getIncrementalRevenueB() { return incrementalRevenueB; }
    public Instant getCreatedAt() { return createdAt; }

    public void setConfigSnapshot(String s) { this.configSnapshot = s; }
    public void setBaselineAResult(String s) { this.baselineAResult = s; }
    public void setBaselineBResult(String s) { this.baselineBResult = s; }
    public void setRecoverflowResult(String s) { this.recoverflowResult = s; }
    public void setAblationSummary(String s) { this.ablationSummary = s; }
    public void setIncrementalRevenueA(BigDecimal v) { this.incrementalRevenueA = v; }
    public void setIncrementalRevenueB(BigDecimal v) { this.incrementalRevenueB = v; }
}
