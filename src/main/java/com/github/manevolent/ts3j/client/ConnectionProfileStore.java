package com.github.manevolent.ts3j.client;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;

/**
 * Stores the last connection form values for the current Windows user.
 *
 * <p>The password is kept in the user's local Java Preferences store so the
 * form can be restored after a restart. It is never included in logs or in a
 * {@link ConnectionConfig#toString()} value.</p>
 */
final class ConnectionProfileStore {
    private static final String NODE = "connection-profile";
    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final String NICKNAME = "nickname";
    private static final String PASSWORD = "password";
    private static final String STATE_PATH = "state-path";

    private final Preferences preferences;

    ConnectionProfileStore() {
        this(Preferences.userNodeForPackage(TeamSpeakDesktopApp.class).node(NODE));
    }

    ConnectionProfileStore(Preferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("preferences");
        this.preferences = preferences;
    }

    ConnectionProfile load(Path fallbackStatePath) {
        Path fallback = fallbackStatePath == null
                ? Paths.get(System.getProperty("user.home"), ".ts3j-client", "voice-sessions.db")
                : fallbackStatePath;
        String host = trimOrDefault(preferences.get(HOST, "localhost"), "localhost");
        String port = preferences.get(PORT, "").trim();
        String nickname = trimOrDefault(preferences.get(NICKNAME, "ts3j-client"), "ts3j-client");
        String password = preferences.get(PASSWORD, "");
        Path statePath = readPath(preferences.get(STATE_PATH, ""), fallback);
        return new ConnectionProfile(host, port, nickname, password, statePath);
    }

    void save(String host, String port, String nickname, String password, Path statePath) {
        preferences.put(HOST, trimOrDefault(host, "localhost"));
        preferences.put(PORT, port == null ? "" : port.trim());
        preferences.put(NICKNAME, trimOrDefault(nickname, "ts3j-client"));
        preferences.put(PASSWORD, password == null ? "" : password);
        if (statePath != null) preferences.put(STATE_PATH, statePath.toString());
        else preferences.remove(STATE_PATH);
        try {
            preferences.flush();
        } catch (Exception ignored) {
            // The in-memory Preferences implementation still keeps this value
            // for the current process; a restricted profile may reject flush.
        }
    }

    private static Path readPath(String value, Path fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try {
            return Paths.get(value.trim());
        } catch (InvalidPathException ignored) {
            return fallback;
        }
    }

    private static String trimOrDefault(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        return value.trim();
    }

    static final class ConnectionProfile {
        private final String host;
        private final String port;
        private final String nickname;
        private final String password;
        private final Path statePath;

        private ConnectionProfile(String host, String port, String nickname,
                                   String password, Path statePath) {
            this.host = host;
            this.port = port;
            this.nickname = nickname;
            this.password = password;
            this.statePath = statePath;
        }

        String getHost() { return host; }
        String getPort() { return port; }
        String getNickname() { return nickname; }
        String getPassword() { return password; }
        Path getStatePath() { return statePath; }
    }
}
