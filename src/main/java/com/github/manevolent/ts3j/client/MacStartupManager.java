package com.github.manevolent.ts3j.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/** Manages the per-user macOS LaunchAgent used to open the app in the tray. */
final class MacStartupManager {
    static final String LAUNCH_AGENT_LABEL = "com.github.manevolent.ts3j.client";
    private static final String LAUNCH_AGENT_FILE = LAUNCH_AGENT_LABEL + ".plist";

    private final Path launchAgentsDirectory;

    MacStartupManager() {
        this(defaultLaunchAgentsDirectory(System.getProperty("user.home")));
    }

    MacStartupManager(Path launchAgentsDirectory) {
        if (launchAgentsDirectory == null) throw new IllegalArgumentException("launchAgentsDirectory");
        this.launchAgentsDirectory = launchAgentsDirectory;
    }

    boolean isSupported() {
        return isMacOs(System.getProperty("os.name", ""));
    }

    Path launchAgent() {
        return launchAgentsDirectory.resolve(LAUNCH_AGENT_FILE);
    }

    boolean isEnabled() {
        return Files.isRegularFile(launchAgent());
    }

    void setEnabled(boolean enabled, Path launcher) throws IOException {
        if (!enabled) {
            Files.deleteIfExists(launchAgent());
            return;
        }
        if (!isSuitableLauncher(launcher)) {
            throw new IOException("Copia ts3j-client a Aplicaciones antes de activar el inicio automático.");
        }

        Files.createDirectories(launchAgentsDirectory);
        Path temporary = Files.createTempFile(launchAgentsDirectory,
                "." + LAUNCH_AGENT_LABEL + "-", ".tmp");
        try {
            Files.write(temporary, plistFor(launcher).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, launchAgent(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, launchAgent(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String plistFor(Path launcher) {
        if (launcher == null) throw new IllegalArgumentException("launcher");
        String executable = escapeXml(launcher.toAbsolutePath().normalize().toString());
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" "
                + "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
                + "<plist version=\"1.0\">\n"
                + "<dict>\n"
                + "    <key>Label</key>\n"
                + "    <string>" + LAUNCH_AGENT_LABEL + "</string>\n"
                + "    <key>ProgramArguments</key>\n"
                + "    <array>\n"
                + "        <string>" + executable + "</string>\n"
                + "        <string>--tray</string>\n"
                + "    </array>\n"
                + "    <key>RunAtLoad</key>\n"
                + "    <true/>\n"
                + "</dict>\n"
                + "</plist>\n";
    }

    static Path defaultLaunchAgentsDirectory(String userHome) {
        if (userHome == null || userHome.trim().isEmpty()) {
            throw new IllegalArgumentException("userHome");
        }
        return Paths.get(userHome, "Library", "LaunchAgents");
    }

    static boolean isMacOs(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("mac");
    }

    /** A LaunchAgent must never retain the ephemeral path of an app opened from a DMG. */
    static boolean isSuitableLauncher(Path launcher) {
        if (launcher == null || !Files.isRegularFile(launcher)) return false;
        Path normalized = launcher.toAbsolutePath().normalize();
        if (normalized.startsWith(Paths.get("/Volumes"))) return false;
        try {
            return !Files.getFileStore(normalized).isReadOnly();
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
