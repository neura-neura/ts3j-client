package com.github.manevolent.ts3j.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Selects the native per-user startup integration without changing either implementation. */
final class StartupManager {
    private enum Platform {
        WINDOWS,
        MAC,
        UNSUPPORTED
    }

    private final Platform platform;
    private final WindowsStartupManager windows;
    private final MacStartupManager mac;

    StartupManager() {
        platform = platformFor(System.getProperty("os.name", ""));
        windows = platform == Platform.WINDOWS ? new WindowsStartupManager() : null;
        mac = platform == Platform.MAC ? new MacStartupManager() : null;
    }

    boolean isSupported() {
        if (windows != null) return windows.isSupported();
        if (mac != null) return mac.isSupported();
        return false;
    }

    boolean isEnabled() {
        if (windows != null) return windows.isEnabled();
        if (mac != null) return mac.isEnabled();
        return false;
    }

    void setEnabled(boolean enabled, Path executable) throws IOException {
        if (windows != null) {
            windows.setEnabled(enabled, executable);
        } else if (mac != null) {
            mac.setEnabled(enabled, executable);
        } else if (enabled) {
            throw new IOException("El inicio automático no está disponible en este sistema operativo.");
        }
    }

    private static Platform platformFor(String osName) {
        String normalized = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (normalized.contains("windows")) return Platform.WINDOWS;
        if (normalized.contains("mac")) return Platform.MAC;
        return Platform.UNSUPPORTED;
    }
}
