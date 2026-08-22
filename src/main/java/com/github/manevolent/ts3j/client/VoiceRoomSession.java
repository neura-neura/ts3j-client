package com.github.manevolent.ts3j.client;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Shared state for one occupied voice channel.
 *
 * <p>A null start is deliberate: TeamSpeak does not report the historical
 * beginning of a session when a client subscribes to an already occupied
 * channel. Such a session is shown as unknown rather than as an invented
 * duration.</p>
 */
public final class VoiceRoomSession {
    private final String serverId;
    private final int channelId;
    private final Instant voiceSessionStart;
    private final boolean startKnown;
    private final Set<Integer> presentUsers;
    private final long revision;

    public VoiceRoomSession(String serverId, int channelId, Instant voiceSessionStart,
                            Collection<Integer> presentUsers) {
        this(serverId, channelId, voiceSessionStart, voiceSessionStart != null,
                presentUsers, 0L);
    }

    public VoiceRoomSession(String serverId, int channelId, Instant voiceSessionStart,
                            boolean startKnown, Collection<Integer> presentUsers,
                            long revision) {
        this.serverId = serverId;
        this.channelId = channelId;
        this.voiceSessionStart = voiceSessionStart;
        this.startKnown = startKnown && voiceSessionStart != null;
        this.presentUsers = Collections.unmodifiableSet(
                new LinkedHashSet<>(presentUsers == null
                        ? Collections.<Integer>emptySet() : presentUsers));
        this.revision = revision;
    }

    public String getServerId() {
        return serverId;
    }

    public int getChannelId() {
        return channelId;
    }

    /** Returns null when the start is not known from an authoritative state. */
    public Instant getVoiceSessionStart() {
        return voiceSessionStart;
    }

    public boolean isStartKnown() {
        return startKnown;
    }

    public Set<Integer> getPresentUsers() {
        return presentUsers;
    }

    public long getRevision() {
        return revision;
    }

    public boolean containsUser(int clientId) {
        return presentUsers.contains(clientId);
    }

    public boolean isOccupied() {
        return !presentUsers.isEmpty();
    }

    public Duration elapsedAt(Instant now) {
        if (!startKnown) {
            throw new IllegalStateException("voice session start is unknown");
        }
        if (now.isBefore(voiceSessionStart)) {
            return Duration.ZERO;
        }
        return Duration.between(voiceSessionStart, now);
    }

    VoiceRoomSession withUsers(Collection<Integer> users, long nextRevision) {
        return new VoiceRoomSession(serverId, channelId, voiceSessionStart,
                startKnown, users, nextRevision);
    }

    VoiceRoomSession withStart(Instant start, boolean known, Collection<Integer> users,
                               long nextRevision) {
        return new VoiceRoomSession(serverId, channelId, start, known, users, nextRevision);
    }

    @Override
    public String toString() {
        return "VoiceRoomSession{" + new SessionKey(serverId, channelId)
                + ", start=" + voiceSessionStart
                + ", known=" + startKnown
                + ", users=" + presentUsers + '}';
    }
}
