package com.github.manevolent.ts3j.client;

import java.util.prefs.Preferences;

/** Persistent desktop preferences shared by the JavaFX shell and tray. */
final class AppPreferences {
    private static final String NODE = "desktop";
    private static final String START_WITH_WINDOWS = "start-with-windows";
    private static final String CLOSE_TO_TRAY = "close-to-tray";
    private static final String LIGHT_THEME = "light-theme";
    private static final String LANGUAGE = "language";
    private static final String VOICE_NOTIFICATIONS = "voice-notifications";
    private static final String VOICE_NOTIFICATION_VOLUME = "voice-notification-volume";
    private static final String CAPTURE_DEVICE = "capture-device";
    private static final String PLAYBACK_DEVICE = "playback-device";
    private static final int DEFAULT_VOICE_NOTIFICATION_VOLUME = 100;

    private final Preferences preferences;

    AppPreferences() {
        this(Preferences.userNodeForPackage(TeamSpeakDesktopApp.class).node(NODE));
    }

    AppPreferences(Preferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("preferences");
        this.preferences = preferences;
    }

    boolean startsWithWindows() {
        return preferences.getBoolean(START_WITH_WINDOWS, false);
    }

    void setStartsWithWindows(boolean value) {
        preferences.putBoolean(START_WITH_WINDOWS, value);
        flush();
    }

    /** Closing to the tray is the desktop-client default, matching the toggle's requested behavior. */
    boolean closesToTray() {
        return preferences.getBoolean(CLOSE_TO_TRAY, true);
    }

    void setClosesToTray(boolean value) {
        preferences.putBoolean(CLOSE_TO_TRAY, value);
        flush();
    }

    boolean isLightTheme() {
        return preferences.getBoolean(LIGHT_THEME, false);
    }

    void setLightTheme(boolean value) {
        preferences.putBoolean(LIGHT_THEME, value);
        flush();
    }

    UiLanguage language() {
        return UiLanguage.fromCode(preferences.get(LANGUAGE, UiLanguage.ENGLISH.getCode()));
    }

    void setLanguage(UiLanguage value) {
        preferences.put(LANGUAGE, (value == null ? UiLanguage.ENGLISH : value).getCode());
        flush();
    }

    boolean voiceNotifications() {
        return preferences.getBoolean(VOICE_NOTIFICATIONS, true);
    }

    void setVoiceNotifications(boolean value) {
        preferences.putBoolean(VOICE_NOTIFICATIONS, value);
        flush();
    }

    int voiceNotificationVolume() {
        return boundedVoiceVolume(preferences.getInt(VOICE_NOTIFICATION_VOLUME,
                DEFAULT_VOICE_NOTIFICATION_VOLUME));
    }

    void setVoiceNotificationVolume(int value) {
        preferences.putInt(VOICE_NOTIFICATION_VOLUME, boundedVoiceVolume(value));
        flush();
    }

    String captureDevice() {
        return preferences.get(CAPTURE_DEVICE, "");
    }

    void setCaptureDevice(String value) {
        preferences.put(CAPTURE_DEVICE, value == null ? "" : value);
        flush();
    }

    String playbackDevice() {
        return preferences.get(PLAYBACK_DEVICE, "");
    }

    void setPlaybackDevice(String value) {
        preferences.put(PLAYBACK_DEVICE, value == null ? "" : value);
        flush();
    }

    private static int boundedVoiceVolume(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private void flush() {
        try {
            preferences.flush();
        } catch (Exception ignored) {
            // A restricted profile still retains the value for this process.
        }
    }
}
