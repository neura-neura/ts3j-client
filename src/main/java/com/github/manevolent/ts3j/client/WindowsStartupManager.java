package com.github.manevolent.ts3j.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Manages a per-user Windows Startup-folder launcher without requiring elevation. */
final class WindowsStartupManager {
    private static final String STARTUP_FILE = "ts3j-client-startup.vbs";
    private final Path startupDirectory;

    WindowsStartupManager() {
        this(defaultStartupDirectory());
    }

    WindowsStartupManager(Path startupDirectory) {
        if (startupDirectory == null) throw new IllegalArgumentException("startupDirectory");
        this.startupDirectory = startupDirectory;
    }

    boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    Path startupScript() {
        return startupDirectory.resolve(STARTUP_FILE);
    }

    boolean isEnabled() {
        return Files.isRegularFile(startupScript());
    }

    void setEnabled(boolean enabled, Path executable) throws IOException {
        if (enabled) {
            if (executable == null || !Files.isRegularFile(executable)) {
                throw new IOException("No se encontró el ejecutable instalado de ts3j-client.");
            }
            Files.createDirectories(startupDirectory);
            Files.write(startupScript(), scriptFor(executable).getBytes(StandardCharsets.UTF_8));
        } else {
            Files.deleteIfExists(startupScript());
        }
    }

    static String scriptFor(Path executable) {
        String command = quoteVbs("\"" + executable.toAbsolutePath().normalize() + "\" --tray");
        return "Set shell = CreateObject(\"WScript.Shell\")\r\n"
                + "shell.Run " + command + ", 0, False\r\n";
    }

    private static String quoteVbs(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static Path defaultStartupDirectory() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.trim().isEmpty()) {
            appData = Paths.get(System.getProperty("user.home"), "AppData", "Roaming").toString();
        }
        return Paths.get(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup");
    }
}
