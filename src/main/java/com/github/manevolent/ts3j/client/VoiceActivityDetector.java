package com.github.manevolent.ts3j.client;

/**
 * Fast-attack, slow-release detector for the local microphone indicator.
 * Audio callbacks provide a monotonic timestamp so the policy is deterministic
 * in tests and does not depend on a JavaFX timer.
 */
final class VoiceActivityDetector {
    static final double ON_THRESHOLD = 0.04D;
    static final double OFF_THRESHOLD = 0.02D;
    static final long RELEASE_NANOS = 180_000_000L;

    private boolean active;
    private long quietSinceNanos;

    boolean update(double level, long nowNanos) {
        double bounded = Math.max(0.0D, Math.min(1.0D, level));
        if (!active) {
            if (bounded >= ON_THRESHOLD) active = true;
            quietSinceNanos = 0L;
            return active;
        }
        if (bounded >= OFF_THRESHOLD) {
            quietSinceNanos = 0L;
            return true;
        }
        if (quietSinceNanos == 0L) quietSinceNanos = nowNanos;
        if (nowNanos - quietSinceNanos >= RELEASE_NANOS) {
            active = false;
            quietSinceNanos = 0L;
        }
        return active;
    }

    boolean isActive() {
        return active;
    }

    void reset() {
        active = false;
        quietSinceNanos = 0L;
    }
}
