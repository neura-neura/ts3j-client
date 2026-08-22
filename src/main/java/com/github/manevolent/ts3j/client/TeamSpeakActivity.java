package com.github.manevolent.ts3j.client;

/**
 * Immutable activity emitted by the gateway for local voice notifications.
 * It deliberately contains presentation-safe strings rather than ts3j event
 * objects, so the audio layer stays independent from the protocol classes.
 */
public final class TeamSpeakActivity {
    public enum Type {
        CONNECTED,
        DISCONNECTED,
        YOU_SWITCHED_CHANNEL,
        CLIENT_JOINED_CURRENT_CHANNEL,
        CLIENT_LEFT_CURRENT_CHANNEL,
        CLIENT_SWITCHED_TO_CURRENT_CHANNEL,
        CLIENT_SWITCHED_FROM_CURRENT_CHANNEL,
        MICROPHONE_MUTED,
        MICROPHONE_UNMUTED,
        AUDIO_MUTED,
        AUDIO_UNMUTED,
        AWAY_ENABLED,
        AWAY_DISABLED,
        CHAT_RECEIVED,
        CHAT_SENT,
        POKE
    }

    private final Type type;
    private final String clientName;
    private final String fromChannelName;
    private final String channelName;
    private final String serverName;

    public TeamSpeakActivity(Type type, String clientName, String fromChannelName,
                             String channelName, String serverName) {
        if (type == null) throw new IllegalArgumentException("type");
        this.type = type;
        this.clientName = clean(clientName);
        this.fromChannelName = clean(fromChannelName);
        this.channelName = clean(channelName);
        this.serverName = clean(serverName);
    }

    public Type getType() { return type; }
    public String getClientName() { return clientName; }
    public String getFromChannelName() { return fromChannelName; }
    public String getChannelName() { return channelName; }
    public String getServerName() { return serverName; }

    /** A short key used to suppress duplicate protocol callbacks. */
    String signature() {
        return type.name() + "|" + clientName + "|" + fromChannelName
                + "|" + channelName + "|" + serverName;
    }

    private static String clean(String value) {
        if (value == null) return "";
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) : normalized;
    }
}
