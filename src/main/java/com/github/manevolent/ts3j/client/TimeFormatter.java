package com.github.manevolent.ts3j.client;

import java.time.Duration;

/** Stable UTC-duration formatting for timers (hours are intentionally unbounded). */
public final class TimeFormatter {
    private TimeFormatter() { }

    public static String format(Duration duration) {
        if (duration == null) return "—";
        long seconds = Math.max(0L, duration.getSeconds());
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder);
    }
}
