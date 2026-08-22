package com.github.manevolent.ts3j.client;

import java.nio.file.Path;

/** User-provided connection settings; passwords are never included in toString. */
public final class ConnectionConfig {
    /** TeamSpeak 3's standard voice/query connection port. */
    public static final int DEFAULT_VOICE_PORT = 9987;

    private final String host;
    private final int port;
    private final String password;
    private final String nickname;
    private final Path sessionStatePath;

    public ConnectionConfig(String host, int port, String password, String nickname,
                            Path sessionStatePath) {
        if (host == null || host.trim().isEmpty()) throw new IllegalArgumentException("host");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("port");
        this.host = host.trim();
        this.port = port;
        this.password = password == null ? "" : password;
        this.nickname = nickname == null || nickname.trim().isEmpty()
                ? "ts3j-client" : nickname.trim();
        this.sessionStatePath = sessionStatePath;
    }

    /**
     * Builds a configuration from the optional port field used by the desktop UI.
     * An empty value intentionally means the TeamSpeak 3 default port (9987).
     */
    public ConnectionConfig(String host, String portText, String password, String nickname,
                            Path sessionStatePath) {
        this(host, parsePort(portText), password, nickname, sessionStatePath);
    }

    public static int parsePort(String portText) {
        if (portText == null || portText.trim().isEmpty()) return DEFAULT_VOICE_PORT;
        final int parsed;
        try {
            parsed = Integer.parseInt(portText.trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("El puerto debe ser un número entre 1 y 65535");
        }
        if (parsed < 1 || parsed > 65535) {
            throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535");
        }
        return parsed;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getPassword() { return password; }
    public String getNickname() { return nickname; }
    public Path getSessionStatePath() { return sessionStatePath; }

    public String serverId() {
        return host + ":" + port;
    }

    @Override
    public String toString() {
        return "ConnectionConfig{" + host + ':' + port + ", nickname='" + nickname + "'}";
    }
}
