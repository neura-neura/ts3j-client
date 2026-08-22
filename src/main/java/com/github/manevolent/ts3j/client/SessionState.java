package com.github.manevolent.ts3j.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Mutable implementation detail shared by the in-memory and file repositories. */
final class SessionState {
    final Map<SessionKey, VoiceRoomSession> sessions = new HashMap<>();
    final Set<String> seenEventIds = new LinkedHashSet<>();
    final Map<String, Long> lastSequences = new HashMap<>();
    long revision;

    SessionState copy() {
        SessionState copy = new SessionState();
        copy.sessions.putAll(sessions);
        copy.seenEventIds.addAll(seenEventIds);
        copy.lastSequences.putAll(lastSequences);
        copy.revision = revision;
        return copy;
    }

    SessionSnapshot snapshot() {
        return new SessionSnapshot(sessions, revision);
    }

    void rememberEvent(String eventId) {
        if (eventId == null || eventId.isEmpty()) return;
        seenEventIds.remove(eventId);
        seenEventIds.add(eventId);
        while (seenEventIds.size() > 1024) {
            seenEventIds.remove(seenEventIds.iterator().next());
        }
    }
}
