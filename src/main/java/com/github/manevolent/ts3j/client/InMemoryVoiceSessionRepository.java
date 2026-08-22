package com.github.manevolent.ts3j.client;

/** Deterministic repository used by the application demo and unit tests. */
public final class InMemoryVoiceSessionRepository implements VoiceSessionRepository {
    private SessionState state = new SessionState();

    @Override
    public synchronized SessionSnapshot snapshot() {
        return state.copy().snapshot();
    }

    @Override
    public synchronized SessionSnapshot apply(PresenceDelta delta) {
        state = SessionMutationEngine.apply(state, delta);
        return state.copy().snapshot();
    }

    @Override
    public synchronized void clear() {
        state = new SessionState();
    }
}
