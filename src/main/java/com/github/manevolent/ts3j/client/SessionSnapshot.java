package com.github.manevolent.ts3j.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable view of all currently occupied voice channels. */
public final class SessionSnapshot {
    private final Map<SessionKey, VoiceRoomSession> sessions;
    private final long revision;

    public SessionSnapshot(Map<SessionKey, VoiceRoomSession> sessions, long revision) {
        this.sessions = Collections.unmodifiableMap(new LinkedHashMap<>(sessions));
        this.revision = revision;
    }

    public Map<SessionKey, VoiceRoomSession> getSessions() {
        return sessions;
    }

    public long getRevision() {
        return revision;
    }

    public VoiceRoomSession get(SessionKey key) {
        return sessions.get(key);
    }
}
