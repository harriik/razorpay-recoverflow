package com.recoverflow.synthetic;

/**
 * Configurable AI proxy quality levels for sanity experiment.
 * Each level targets an approximate failure-category accuracy, measured not assumed.
 * LOW ~50%, MEDIUM ~75%, HIGH ~90%. Deterministic seeded behavior.
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
