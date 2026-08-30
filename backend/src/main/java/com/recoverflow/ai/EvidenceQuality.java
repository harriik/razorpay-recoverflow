package com.recoverflow.ai;

/**
 * Qualitative evidence sufficiency, NOT a calibrated probability.
 * Replaces LLM numeric confidence (e.g. 0.91) per Phase -1 correction.
 * Policy must gate on LOW/MEDIUM/HIGH, never on a float threshold like 0.60.
 */
public enum EvidenceQuality {
    HIGH,
    MEDIUM,
    LOW
}
