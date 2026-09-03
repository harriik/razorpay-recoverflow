package com.recoverflow.synthetic;

/**
 * Configurable AI proxy quality levels for sanity experiment.
 * Measured true-category accuracy (not proxy-target) is reported from experiment:
 * LOW — approximately 44% measured true-category accuracy
 * MEDIUM — approximately 65% measured true-category accuracy
 * HIGH — approximately 77% measured true-category accuracy
 * Proxy target against observable gateway is 50%/75%/90%, but due to noisy observable
 * gateway (85% correlated with hidden truth) true-category measured is lower (~44/65/77).
 * Deterministic seeded behavior; exact measured must come from experiment output.
 */
public enum AiQuality {
    LOW(0.50, "synthetic-ai-quality-low-v1"),
    MEDIUM(0.75, "synthetic-ai-quality-medium-v1"),
    HIGH(0.90, "synthetic-ai-quality-high-v1");

    private final double targetAccuracy;
    private final String versionSuffix;

    AiQuality(double targetAccuracy, String versionSuffix) {
        this.targetAccuracy = targetAccuracy;
        this.versionSuffix = versionSuffix;
    }

    public double targetAccuracy() { return targetAccuracy; }
    public String versionSuffix() { return versionSuffix; }
}
