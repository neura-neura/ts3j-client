package com.github.manevolent.ts3j.client;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Application-facing reducer. It owns the shared clock boundary and publishes
 * immutable snapshots to the JavaFX layer without leaking TeamSpeak classes.
 */
public final class VoiceSessionCoordinator {
    private VoiceSessionRepository repository;
    private final Clock clock;
    private final List<Consumer<SessionSnapshot>> listeners = new ArrayList<>();

    public VoiceSessionCoordinator(VoiceSessionRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public VoiceSessionCoordinator(VoiceSessionRepository repository, Clock clock) {
        if (repository == null || clock == null) throw new IllegalArgumentException("repository/clock");
        this.repository = repository;
        this.clock = clock;
    }

    public synchronized void addListener(Consumer<SessionSnapshot> listener) {
        if (listener != null) listeners.add(listener);
    }

    public synchronized void removeListener(Consumer<SessionSnapshot> listener) {
        listeners.remove(listener);
    }

    public synchronized SessionSnapshot snapshot() {
        return repository.snapshot();
    }

    /** Switches the authoritative store before a new server connection. */
    public synchronized void useRepository(VoiceSessionRepository repository) {
        if (repository == null) throw new IllegalArgumentException("repository");
        this.repository = repository;
        SessionSnapshot snapshot = repository.snapshot();
        for (Consumer<SessionSnapshot> listener : new ArrayList<>(listeners)) {
            listener.accept(snapshot);
        }
    }

    public synchronized SessionSnapshot apply(PresenceDelta delta) {
        SessionSnapshot snapshot = repository.apply(delta);
        for (Consumer<SessionSnapshot> listener : new ArrayList<>(listeners)) {
            listener.accept(snapshot);
        }
        return snapshot;
    }

    public SessionSnapshot join(String serverId, int channelId, int clientId) {
        return join(serverId, channelId, clientId, clock.instant(), false, null, 0L);
    }

    public SessionSnapshot join(String serverId, int channelId, int clientId,
                                Instant observedAt, boolean historical,
                                String eventId, long sequence) {
        return apply(PresenceDelta.join(serverId, channelId, clientId, observedAt,
                historical, eventId, sequence));
    }

    public SessionSnapshot leave(String serverId, int channelId, int clientId,
                                 Instant observedAt, String eventId, long sequence) {
        return apply(PresenceDelta.leave(serverId, channelId, clientId, observedAt,
                eventId, sequence));
    }

    public SessionSnapshot move(String serverId, int fromChannelId, int toChannelId,
                                int clientId, Instant observedAt, boolean historical,
                                String eventId, long sequence) {
        return apply(PresenceDelta.move(serverId, fromChannelId, toChannelId, clientId,
                observedAt, historical, eventId, sequence));
    }

    public SessionSnapshot reconcile(String serverId,
                                     java.util.Map<Integer, ? extends java.util.Collection<Integer>> usersByChannel,
                                     Instant observedAt, String eventId, long sequence) {
        return reconcile(serverId, usersByChannel, observedAt, eventId, sequence, -1);
    }

    /**
     * Reconciles a server snapshot and optionally identifies this instance's
     * client. A sole local client can establish a fresh session after a stale
     * unknown record, while snapshots containing other users remain historical.
     */
    public SessionSnapshot reconcile(String serverId,
                                     java.util.Map<Integer, ? extends java.util.Collection<Integer>> usersByChannel,
                                     Instant observedAt, String eventId, long sequence,
                                     int bootstrapClientId) {
        return apply(PresenceDelta.snapshot(serverId, usersByChannel, observedAt,
                eventId, sequence, bootstrapClientId));
    }

    /**
     * Shares a known channel-session start discovered by another app instance.
     * The reducer ignores it when the channel is no longer occupied.
     */
    public SessionSnapshot adoptSessionStart(String serverId, int channelId,
                                             Instant observedAt, String eventId) {
        return apply(PresenceDelta.start(serverId, channelId, observedAt, eventId));
    }

    public Clock getClock() {
        return clock;
    }
}
