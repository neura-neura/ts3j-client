package com.github.manevolent.ts3j.client;

/** Authoritative persistence boundary for cross-instance voice sessions. */
public interface VoiceSessionRepository {
    SessionSnapshot snapshot();

    SessionSnapshot apply(PresenceDelta delta);

    void clear();
}
