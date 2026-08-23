package com.github.manevolent.ts3j.client;

/** Immutable Java Sound device entry used by the audio settings dialog. */
final class AudioDevice {
    private final String id;
    private final String displayName;
    private final boolean systemDefault;

    AudioDevice(String id, String displayName, boolean systemDefault) {
        this.id = id == null ? "" : id;
        this.displayName = displayName == null || displayName.trim().isEmpty()
                ? "Default" : displayName.trim();
        this.systemDefault = systemDefault;
    }

    String getId() { return id; }
    String getDisplayName() { return displayName; }
    boolean isSystemDefault() { return systemDefault; }

    @Override
    public String toString() { return displayName; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AudioDevice)) return false;
        AudioDevice that = (AudioDevice) other;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }
}
