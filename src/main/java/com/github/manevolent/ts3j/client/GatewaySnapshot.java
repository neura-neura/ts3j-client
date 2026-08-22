package com.github.manevolent.ts3j.client;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable presentation snapshot emitted by TeamSpeakGateway. */
public final class GatewaySnapshot {
    private final ConnectionStatus status;
    private final String serverId;
    private final String errorMessage;
    private final int localClientId;
    private final int currentChannelId;
    private final Map<Integer, ChannelView> channels;
    private final Map<Integer, ClientView> clients;
    private final SessionSnapshot sessions;
    private final Map<Integer, List<ChannelTextMessage>> channelMessages;
    private final Map<Integer, Integer> channelHistoryBoundaries;
    private final boolean sessionStateReady;

    public GatewaySnapshot(ConnectionStatus status, String serverId, String errorMessage,
                           int localClientId, int currentChannelId,
                           Map<Integer, ChannelView> channels,
                           Map<Integer, ClientView> clients,
                           SessionSnapshot sessions) {
        this(status, serverId, errorMessage, localClientId, currentChannelId,
                channels, clients, sessions, Collections.<Integer, List<ChannelTextMessage>>emptyMap(), true);
    }

    public GatewaySnapshot(ConnectionStatus status, String serverId, String errorMessage,
                           int localClientId, int currentChannelId,
                           Map<Integer, ChannelView> channels,
                           Map<Integer, ClientView> clients,
                           SessionSnapshot sessions, boolean sessionStateReady) {
        this(status, serverId, errorMessage, localClientId, currentChannelId,
                channels, clients, sessions, Collections.<Integer, List<ChannelTextMessage>>emptyMap(),
                sessionStateReady);
    }

    public GatewaySnapshot(ConnectionStatus status, String serverId, String errorMessage,
                           int localClientId, int currentChannelId,
                           Map<Integer, ChannelView> channels,
                           Map<Integer, ClientView> clients,
                           SessionSnapshot sessions,
                           Map<Integer, List<ChannelTextMessage>> channelMessages,
                           boolean sessionStateReady) {
        this(status, serverId, errorMessage, localClientId, currentChannelId,
                channels, clients, sessions, channelMessages,
                Collections.<Integer, Integer>emptyMap(), sessionStateReady);
    }

    public GatewaySnapshot(ConnectionStatus status, String serverId, String errorMessage,
                           int localClientId, int currentChannelId,
                           Map<Integer, ChannelView> channels,
                           Map<Integer, ClientView> clients,
                           SessionSnapshot sessions,
                           Map<Integer, List<ChannelTextMessage>> channelMessages,
                           Map<Integer, Integer> channelHistoryBoundaries,
                           boolean sessionStateReady) {
        this.status = status;
        this.serverId = serverId == null ? "" : serverId;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
        this.localClientId = localClientId;
        this.currentChannelId = currentChannelId;
        this.channels = Collections.unmodifiableMap(new LinkedHashMap<>(channels));
        this.clients = Collections.unmodifiableMap(new LinkedHashMap<>(clients));
        this.sessions = sessions == null ? new SessionSnapshot(Collections.<SessionKey, VoiceRoomSession>emptyMap(), 0L)
                : sessions;
        this.channelMessages = copyMessages(channelMessages);
        this.channelHistoryBoundaries = copyBoundaries(channelHistoryBoundaries);
        this.sessionStateReady = sessionStateReady;
    }

    public ConnectionStatus getStatus() { return status; }
    public String getServerId() { return serverId; }
    public String getErrorMessage() { return errorMessage; }
    public int getLocalClientId() { return localClientId; }
    public int getCurrentChannelId() { return currentChannelId; }
    public Map<Integer, ChannelView> getChannels() { return channels; }
    public Map<Integer, ClientView> getClients() { return clients; }
    public SessionSnapshot getSessions() { return sessions; }
    public Map<Integer, List<ChannelTextMessage>> getChannelMessages() { return channelMessages; }
    /**
     * For each channel, the index where the current live connection begins.
     * Entries are present only when local history was loaded before connecting.
     */
    public Map<Integer, Integer> getChannelHistoryBoundaries() { return channelHistoryBoundaries; }

    /**
     * Returns whether the session snapshot has been reconciled with the current
     * server connection. Persisted sessions must not be rendered while this is
     * false because the initial channel/client snapshot is still loading.
     */
    public boolean isSessionStateReady() { return sessionStateReady; }

    private static Map<Integer, List<ChannelTextMessage>> copyMessages(
            Map<Integer, List<ChannelTextMessage>> source) {
        Map<Integer, List<ChannelTextMessage>> copy = new LinkedHashMap<>();
        if (source != null) {
            for (Map.Entry<Integer, List<ChannelTextMessage>> entry : source.entrySet()) {
                List<ChannelTextMessage> messages = entry.getValue() == null
                        ? Collections.<ChannelTextMessage>emptyList()
                        : new ArrayList<>(entry.getValue());
                copy.put(entry.getKey(), Collections.unmodifiableList(messages));
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<Integer, Integer> copyBoundaries(Map<Integer, Integer> source) {
        if (source == null || source.isEmpty()) return Collections.emptyMap();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
