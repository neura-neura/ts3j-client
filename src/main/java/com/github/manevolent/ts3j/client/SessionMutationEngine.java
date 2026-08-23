package com.github.manevolent.ts3j.client;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Idempotent reducer for join/leave/move/snapshot operations. */
final class SessionMutationEngine {
    private SessionMutationEngine() { }

    static SessionState apply(SessionState state, PresenceDelta delta) {
        if (delta == null) return state;
        if (!delta.getEventId().isEmpty() && state.seenEventIds.contains(delta.getEventId())) {
            return state;
        }

        if (delta.getSequence() > 0 && delta.getClientId() >= 0
                && delta.getType() != PresenceDelta.Type.SNAPSHOT) {
            String sequenceKey = delta.getServerId() + "\u0000" + delta.getClientId();
            Long previous = state.lastSequences.get(sequenceKey);
            if (previous != null && delta.getSequence() <= previous) {
                state.rememberEvent(delta.getEventId());
                return state;
            }
            state.lastSequences.put(sequenceKey, delta.getSequence());
        }

        switch (delta.getType()) {
            case JOIN:
                addUser(state, delta.getTo(), delta.getClientId(), delta.getObservedAt(),
                        delta.isHistorical());
                break;
            case LEAVE:
                removeUser(state, delta.getServerId(), delta.getFrom(), delta.getClientId());
                break;
            case MOVE:
                removeUser(state, delta.getServerId(), delta.getFrom(), delta.getClientId());
                addUser(state, delta.getTo(), delta.getClientId(), delta.getObservedAt(),
                        delta.isHistorical());
                break;
            case SNAPSHOT:
                reconcileSnapshot(state, delta);
                break;
            case START:
                adoptStart(state, delta.getTo(), delta.getObservedAt());
                break;
            default:
                throw new IllegalStateException("Unsupported delta: " + delta.getType());
        }

        state.revision++;
        state.rememberEvent(delta.getEventId());
        return state;
    }

    private static void addUser(SessionState state, SessionKey key, int clientId,
                                Instant observedAt, boolean historical) {
        if (key == null || clientId < 0) return;

        // A join with no source is also treated as a move if stale local state
        // still has the client in another channel.
        removeUserFromOtherChannels(state, key, clientId);

        VoiceRoomSession session = state.sessions.get(key);
        if (session == null) {
            Set<Integer> users = new LinkedHashSet<>();
            users.add(clientId);
            Instant start = historical ? null : observedAt;
            state.sessions.put(key, new VoiceRoomSession(key.getServerId(), key.getChannelId(),
                    start, !historical, users, state.revision + 1));
            return;
        }

        if (!session.containsUser(clientId)) {
            Set<Integer> users = new LinkedHashSet<>(session.getPresentUsers());
            users.add(clientId);
            state.sessions.put(key, session.withUsers(users, state.revision + 1));
        }
    }

    private static void removeUser(SessionState state, String serverId, SessionKey requested,
                                   int clientId) {
        if (clientId < 0) return;
        if (requested != null) {
            removeUserFromSession(state, requested, clientId);
            return;
        }
        removeUserFromAllChannels(state, serverId, clientId);
    }

    private static void removeUserFromOtherChannels(SessionState state, SessionKey target,
                                                    int clientId) {
        Iterator<Map.Entry<SessionKey, VoiceRoomSession>> iterator =
                state.sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SessionKey, VoiceRoomSession> entry = iterator.next();
            if (entry.getKey().equals(target) || !entry.getKey().getServerId().equals(target.getServerId())) {
                continue;
            }
            VoiceRoomSession session = entry.getValue();
            if (session.containsUser(clientId)) {
                Set<Integer> users = new LinkedHashSet<>(session.getPresentUsers());
                users.remove(clientId);
                if (users.isEmpty()) iterator.remove();
                else entry.setValue(session.withUsers(users, state.revision + 1));
            }
        }
    }

    private static void removeUserFromAllChannels(SessionState state, String serverId,
                                                  int clientId) {
        Iterator<Map.Entry<SessionKey, VoiceRoomSession>> iterator =
                state.sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SessionKey, VoiceRoomSession> entry = iterator.next();
            if (!entry.getKey().getServerId().equals(serverId)) continue;
            VoiceRoomSession session = entry.getValue();
            if (!session.containsUser(clientId)) continue;
            Set<Integer> users = new LinkedHashSet<>(session.getPresentUsers());
            users.remove(clientId);
            if (users.isEmpty()) iterator.remove();
            else entry.setValue(session.withUsers(users, state.revision + 1));
        }
    }

    private static void removeUserFromSession(SessionState state, SessionKey key, int clientId) {
        VoiceRoomSession session = state.sessions.get(key);
        if (session == null || !session.containsUser(clientId)) return;
        Set<Integer> users = new LinkedHashSet<>(session.getPresentUsers());
        users.remove(clientId);
        if (users.isEmpty()) state.sessions.remove(key);
        else state.sessions.put(key, session.withUsers(users, state.revision + 1));
    }

    private static void reconcileSnapshot(SessionState state, PresenceDelta delta) {
        Set<SessionKey> keysForServer = new HashSet<>();
        for (Integer channelId : delta.getSnapshot().keySet()) {
            keysForServer.add(new SessionKey(delta.getServerId(), channelId));
        }

        Iterator<Map.Entry<SessionKey, VoiceRoomSession>> iterator =
                state.sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SessionKey, VoiceRoomSession> entry = iterator.next();
            if (entry.getKey().getServerId().equals(delta.getServerId())
                    && !keysForServer.contains(entry.getKey())) {
                iterator.remove();
            }
        }

        for (Map.Entry<Integer, Set<Integer>> entry : delta.getSnapshot().entrySet()) {
            SessionKey key = new SessionKey(delta.getServerId(), entry.getKey());
            Set<Integer> users = new LinkedHashSet<>(entry.getValue());
            if (users.isEmpty()) {
                state.sessions.remove(key);
                continue;
            }
            VoiceRoomSession current = state.sessions.get(key);
            if (current == null) {
                boolean soleLocalClient = delta.getBootstrapClientId() >= 0
                        && users.size() == 1
                        && users.contains(delta.getBootstrapClientId());
                // A sole local client is a real zero-to-one observation. Any
                // other snapshot is historical because the channel may have
                // been occupied before this instance connected.
                state.sessions.put(key, new VoiceRoomSession(
                        key.getServerId(), key.getChannelId(),
                        soleLocalClient ? delta.getObservedAt() : null,
                        soleLocalClient, users, state.revision + 1));
            } else if (delta.getBootstrapClientId() >= 0
                    && users.size() == 1
                    && users.contains(delta.getBootstrapClientId())) {
                // A stale known or unknown record can survive an application
                // restart. The old client was disconnected before this fresh
                // local client joined, so a sole local snapshot starts anew.
                state.sessions.put(key, current.withStart(delta.getObservedAt(), true,
                        users, state.revision + 1));
            } else {
                state.sessions.put(key, current.withUsers(users, state.revision + 1));
            }
        }
    }

    private static void adoptStart(SessionState state, SessionKey key, Instant start) {
        if (key == null || start == null) return;
        VoiceRoomSession current = state.sessions.get(key);
        if (current == null || !current.isOccupied()) return;
        Instant existing = current.getVoiceSessionStart();
        // Several app instances may announce the same transition at nearly
        // the same time. The earliest trusted marker is deterministic and
        // avoids shortening a session because messages arrived out of order.
        if (current.isStartKnown() && existing != null && !start.isBefore(existing)) return;
        state.sessions.put(key, current.withStart(start, true,
                current.getPresentUsers(), state.revision + 1));
    }
}
