package com.github.manevolent.ts3j.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.prefs.Preferences;

/** Stores per-user playback modifiers locally; values are never sent to the TeamSpeak server. */
final class ClientVolumeStore {
    private static final String NODE = "client-volume";
    private static final double DEFAULT_DB = 0.0D;

    private final Preferences preferences;

    ClientVolumeStore() {
        this(Preferences.userNodeForPackage(TeamSpeakDesktopApp.class).node(NODE));
    }

    ClientVolumeStore(Preferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("preferences");
        this.preferences = preferences;
    }

    double get(String serverId, ClientView client) {
        if (client == null) return DEFAULT_DB;
        return get(serverId, client.getStableIdentity());
    }

    double get(String serverId, String stableIdentity) {
        return preferences.getDouble(key(serverId, stableIdentity), DEFAULT_DB);
    }

    void set(String serverId, ClientView client, double decibels) {
        if (client == null) return;
        set(serverId, client.getStableIdentity(), decibels);
    }

    void set(String serverId, String stableIdentity, double decibels) {
        double bounded = Math.max(-50.0D, Math.min(20.0D, decibels));
        String key = key(serverId, stableIdentity);
        if (Math.abs(bounded) < 0.05D) preferences.remove(key);
        else preferences.putDouble(key, bounded);
        flush();
    }

    boolean isModified(String serverId, ClientView client) {
        return Math.abs(get(serverId, client)) >= 0.05D;
    }

    private static String key(String serverId, String stableIdentity) {
        return "v-" + digest((serverId == null ? "" : serverId) + "\u0000"
                + (stableIdentity == null ? "" : stableIdentity));
    }

    private static String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte byteValue : bytes) {
                result.append(String.format(Locale.ROOT, "%02x", byteValue & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no está disponible", impossible);
        }
    }

    private void flush() {
        try {
            preferences.flush();
        } catch (Exception ignored) {
            // A restricted profile still retains the value for this process.
        }
    }
}
