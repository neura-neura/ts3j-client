package com.github.manevolent.ts3j.client;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Normalized presence event consumed by the session reducer. */
public final class PresenceDelta {
    public enum Type { JOIN, LEAVE, MOVE, SNAPSHOT, START, SERVER_START }

    private final Type type;
    private final String serverId;
    private final int clientId;
    private final SessionKey from;
    private final SessionKey to;
    private final Instant observedAt;
    private final boolean historical;
    private final String eventId;
    private final long sequence;
    private final int bootstrapClientId;
    private final Map<Integer, Set<Integer>> snapshot;

    private PresenceDelta(Type type, String serverId, int clientId, SessionKey from,
                          SessionKey to, Instant observedAt, boolean historical,
                          String eventId, long sequence,
                          Map<Integer, Set<Integer>> snapshot,
                          int bootstrapClientId) {
        this.type = type;
        this.serverId = serverId;
        this.clientId = clientId;
        this.from = from;
        this.to = to;
        this.observedAt = observedAt == null ? Instant.now() : observedAt;
        this.historical = historical;
        this.eventId = eventId == null ? "" : eventId;
        this.sequence = sequence;
        this.bootstrapClientId = bootstrapClientId;
        this.snapshot = snapshot == null ? Collections.<Integer, Set<Integer>>emptyMap()
                : immutableSnapshot(snapshot);
    }

    public static PresenceDelta join(String serverId, int channelId, int clientId,
                                     Instant observedAt, boolean historical,
                                     String eventId, long sequence) {
        return new PresenceDelta(Type.JOIN, serverId, clientId, null,
                new SessionKey(serverId, channelId), observedAt, historical,
                eventId, sequence, null, -1);
    }

    public static PresenceDelta leave(String serverId, int channelId, int clientId,
                                      Instant observedAt, String eventId, long sequence) {
        return new PresenceDelta(Type.LEAVE, serverId, clientId,
                channelId < 0 ? null : new SessionKey(serverId, channelId), null,
                observedAt, false, eventId, sequence, null, -1);
    }

    public static PresenceDelta move(String serverId, int fromChannelId, int toChannelId,
                                     int clientId, Instant observedAt, boolean historical,
                                     String eventId, long sequence) {
        return new PresenceDelta(Type.MOVE, serverId, clientId,
                fromChannelId < 0 ? null : new SessionKey(serverId, fromChannelId),
                new SessionKey(serverId, toChannelId), observedAt, historical,
                eventId, sequence, null, -1);
    }

    public static PresenceDelta snapshot(String serverId,
                                         Map<Integer, ? extends Collection<Integer>> usersByChannel,
                                         Instant observedAt, String eventId, long sequence) {
        return snapshot(serverId, usersByChannel, observedAt, eventId, sequence, -1);
    }

    /**
     * Creates a historical snapshot with an optional local client bootstrap.
     * When that client is the sole occupant, the reducer may promote a stale
     * unknown record to a fresh start observed at {@code observedAt}.
     */
    public static PresenceDelta snapshot(String serverId,
                                         Map<Integer, ? extends Collection<Integer>> usersByChannel,
                                         Instant observedAt, String eventId, long sequence,
                                         int bootstrapClientId) {
        Map<Integer, Set<Integer>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, ? extends Collection<Integer>> entry : usersByChannel.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return new PresenceDelta(Type.SNAPSHOT, serverId, -1, null, null,
                observedAt, true, eventId, sequence, copy, bootstrapClientId);
    }

    /**
     * Adopts a start observed by another ts3j-client instance. The reducer
     * applies it only to an occupied session, so a stale private marker cannot
     * resurrect an empty channel.
     */
    public static PresenceDelta start(String serverId, int channelId, Instant observedAt,
                                      String eventId) {
        return new PresenceDelta(Type.START, serverId, -1, null,
                new SessionKey(serverId, channelId), observedAt, false,
                eventId, 0L, null, -1);
    }

    /**
     * Applies a start received from the server-side timer authority. Unlike a
     * peer marker, this value is allowed to replace a locally inferred start.
     */
    public static PresenceDelta serverStart(String serverId, int channelId,
                                             Instant observedAt, String eventId) {
        return new PresenceDelta(Type.SERVER_START, serverId, -1, null,
                new SessionKey(serverId, channelId), observedAt, false,
                eventId, 0L, null, -1);
    }

    public Type getType() { return type; }
    public String getServerId() { return serverId; }
    public int getClientId() { return clientId; }
    public SessionKey getFrom() { return from; }
    public SessionKey getTo() { return to; }
    public Instant getObservedAt() { return observedAt; }
    public boolean isHistorical() { return historical; }
    public String getEventId() { return eventId; }
    public long getSequence() { return sequence; }
    public int getBootstrapClientId() { return bootstrapClientId; }
    public Map<Integer, Set<Integer>> getSnapshot() { return snapshot; }

    private static Map<Integer, Set<Integer>> immutableSnapshot(Map<Integer, Set<Integer>> source) {
        Map<Integer, Set<Integer>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, Set<Integer>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }
}
