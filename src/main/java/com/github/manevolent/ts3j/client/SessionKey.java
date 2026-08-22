package com.github.manevolent.ts3j.client;

import java.util.Objects;

/** Stable identity for a voice channel in one TeamSpeak server. */
public final class SessionKey {
    private final String serverId;
    private final int channelId;

    public SessionKey(String serverId, int channelId) {
        if (serverId == null || serverId.trim().isEmpty()) {
            throw new IllegalArgumentException("serverId must not be blank");
        }
        this.serverId = serverId;
        this.channelId = channelId;
    }

    public String getServerId() {
        return serverId;
    }

    public int getChannelId() {
        return channelId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SessionKey)) return false;
        SessionKey that = (SessionKey) other;
        return channelId == that.channelId && serverId.equals(that.serverId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverId, channelId);
    }

    @Override
    public String toString() {
        return serverId + "/" + channelId;
    }
}
