package com.github.manevolent.ts3j.client;

import java.time.Instant;

/** Immutable channel-chat message copied from a ts3j text event. */
public final class ChannelTextMessage {
    private final int channelId;
    private final String sender;
    private final String message;
    private final Instant receivedAt;

    public ChannelTextMessage(int channelId, String sender, String message, Instant receivedAt) {
        this.channelId = channelId;
        this.sender = sender == null || sender.trim().isEmpty() ? "Usuario" : sender;
        this.message = message == null ? "" : message;
        this.receivedAt = receivedAt == null ? Instant.EPOCH : receivedAt;
    }

    public int getChannelId() { return channelId; }
    public String getSender() { return sender; }
    public String getMessage() { return message; }
    public Instant getReceivedAt() { return receivedAt; }
}
