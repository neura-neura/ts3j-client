package com.github.manevolent.ts3j.client;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VoiceSessionCoordinatorTest {
    private static final String SERVER = "voice.example:9987";
    private static final Instant TEN = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant EIGHTEEN = Instant.parse("2026-01-01T18:00:00Z");

    private VoiceSessionCoordinator coordinator(VoiceSessionRepository repository) {
        return new VoiceSessionCoordinator(repository, Clock.fixed(TEN, ZoneOffset.UTC));
    }

    private VoiceRoomSession session(SessionSnapshot snapshot, int channelId) {
        VoiceRoomSession session = snapshot.get(new SessionKey(SERVER, channelId));
        assertNotNull(session);
        return session;
    }

    @Test
    public void firstUserStartsAtObservedTime() {
        VoiceSessionCoordinator coordinator = coordinator(new InMemoryVoiceSessionRepository());

        VoiceRoomSession session = session(coordinator.join(SERVER, 10, 101, TEN, false, "join-1", 1), 10);

        assertEquals(TEN, session.getVoiceSessionStart());
        assertTrue(session.isStartKnown());
        assertEquals(Collections.singleton(101), session.getPresentUsers());
    }

    @Test
    public void secondUserAfterHoursKeepsSharedStart() {
        VoiceSessionCoordinator coordinator = coordinator(new InMemoryVoiceSessionRepository());
        coordinator.join(SERVER, 10, 101, TEN, false, "join-1", 1);

        VoiceRoomSession session = session(coordinator.join(SERVER, 10, 102, EIGHTEEN,
                false, "join-2", 1), 10);

        assertEquals(TEN, session.getVoiceSessionStart());
        assertEquals("08:00:00", TimeFormatter.format(session.elapsedAt(EIGHTEEN)));
        assertEquals(2, session.getPresentUsers().size());
    }

    @Test
    public void leavingOneUserKeepsTimerAndEmptyRemovesIt() {
        VoiceSessionCoordinator coordinator = coordinator(new InMemoryVoiceSessionRepository());
        coordinator.join(SERVER, 10, 101, TEN, false, "join-1", 1);
        coordinator.join(SERVER, 10, 102, EIGHTEEN, false, "join-2", 1);

        VoiceRoomSession afterOneLeaves = session(coordinator.leave(SERVER, 10, 101,
                EIGHTEEN, "leave-1", 2), 10);
        assertEquals(TEN, afterOneLeaves.getVoiceSessionStart());
        assertEquals(Collections.singleton(102), afterOneLeaves.getPresentUsers());

        assertTrue(coordinator.leave(SERVER, 10, 102, EIGHTEEN, "leave-2", 2)
                .getSessions().isEmpty());
    }

    @Test
    public void aNewUserAfterEmptyStartsNewSession() {
        VoiceSessionCoordinator coordinator = coordinator(new InMemoryVoiceSessionRepository());
        coordinator.join(SERVER, 10, 101, TEN, false, "join-1", 1);
        coordinator.leave(SERVER, 10, 101, EIGHTEEN, "leave-1", 2);

        Instant newStart = Instant.parse("2026-01-02T09:30:00Z");
        VoiceRoomSession session = session(coordinator.join(SERVER, 10, 103, newStart,
                false, "join-3", 3), 10);

        assertEquals(newStart, session.getVoiceSessionStart());
        assertEquals(Collections.singleton(103), session.getPresentUsers());
    }

    @Test
    public void movingClientUpdatesBothChannelsAtomically() {
        VoiceSessionCoordinator coordinator = coordinator(new InMemoryVoiceSessionRepository());
        coordinator.join(SERVER, 10, 101, TEN, false, "join-1", 1);

        SessionSnapshot moved = coordinator.move(SERVER, 10, 20, 101, EIGHTEEN,
                false, "move-1", 2);

        assertFalse(moved.getSessions().containsKey(new SessionKey(SERVER, 10)));
        assertEquals(Collections.singleton(101), session(moved, 20).getPresentUsers());
        assertEquals(EIGHTEEN, session(moved, 20).getVoiceSessionStart());
    }

    @Test
    public void duplicateAndLateEventsAreIdempotentAndOrdered() {
        VoiceSessionCoordinator coordinator = coordinator(new InMemoryVoiceSessionRepository());
        coordinator.join(SERVER, 10, 101, TEN, false, "join-1", 1);
        coordinator.move(SERVER, 10, 20, 101, EIGHTEEN, false, "move-1", 3);

        // A late leave for the old channel has a lower client sequence and is ignored.
        coordinator.leave(SERVER, 10, 101, EIGHTEEN, "late-leave", 2);
        // Replaying the move is ignored by event id and does not create a second session.
        SessionSnapshot snapshot = coordinator.move(SERVER, 10, 20, 101, EIGHTEEN,
                false, "move-1", 3);

        assertEquals(1, snapshot.getSessions().size());
        assertEquals(Collections.singleton(101), session(snapshot, 20).getPresentUsers());
    }

    @Test
    public void aHistoricalSnapshotNeverInventsStart() {
        VoiceSessionCoordinator coordinator = coordinator(new InMemoryVoiceSessionRepository());
        Map<Integer, java.util.Collection<Integer>> users = new LinkedHashMap<>();
        users.put(10, Arrays.<Integer>asList(101, 102));

        VoiceRoomSession session = session(coordinator.reconcile(SERVER, users, EIGHTEEN,
                "snapshot-1", 0), 10);

        assertFalse(session.isStartKnown());
        assertEquals(null, session.getVoiceSessionStart());
        assertEquals(2, session.getPresentUsers().size());
    }

    @Test
    public void soleLocalClientPromotesStaleUnknownRecordToFreshStart() {
        VoiceSessionCoordinator coordinator = coordinator(new InMemoryVoiceSessionRepository());
        Map<Integer, java.util.Collection<Integer>> historical = new LinkedHashMap<>();
        historical.put(10, Arrays.<Integer>asList(101, 102));
        coordinator.reconcile(SERVER, historical, TEN, "old-snapshot", 0);

        Map<Integer, java.util.Collection<Integer>> localOnly = new LinkedHashMap<>();
        localOnly.put(10, Collections.<Integer>singleton(101));
        VoiceRoomSession session = session(coordinator.reconcile(SERVER, localOnly, EIGHTEEN,
                "local-bootstrap", 0, 101), 10);

        assertTrue(session.isStartKnown());
        assertEquals(EIGHTEEN, session.getVoiceSessionStart());
        assertEquals(Collections.singleton(101), session.getPresentUsers());
    }

    @Test
    public void soleLocalClientStartsWhenNoPreviousRecordExists() {
        VoiceSessionCoordinator coordinator = coordinator(new InMemoryVoiceSessionRepository());
        Map<Integer, java.util.Collection<Integer>> localOnly = new LinkedHashMap<>();
        localOnly.put(10, Collections.<Integer>singleton(101));

        VoiceRoomSession session = session(coordinator.reconcile(SERVER, localOnly, EIGHTEEN,
                "first-local-snapshot", 0, 101), 10);

        assertTrue(session.isStartKnown());
        assertEquals(EIGHTEEN, session.getVoiceSessionStart());
    }

    @Test
    public void soleLocalClientResetsStaleKnownRecord() {
        VoiceSessionCoordinator coordinator = coordinator(new InMemoryVoiceSessionRepository());
        coordinator.join(SERVER, 10, 102, TEN, false, "old-join", 1);

        Map<Integer, java.util.Collection<Integer>> localOnly = new LinkedHashMap<>();
        localOnly.put(10, Collections.<Integer>singleton(101));
        VoiceRoomSession session = session(coordinator.reconcile(SERVER, localOnly, EIGHTEEN,
                "new-local-snapshot", 0, 101), 10);

        assertTrue(session.isStartKnown());
        assertEquals(EIGHTEEN, session.getVoiceSessionStart());
        assertEquals(Collections.singleton(101), session.getPresentUsers());
    }

    @Test
    public void localBootstrapDoesNotPromoteWhenAnotherUserIsPresent() {
        VoiceSessionCoordinator coordinator = coordinator(new InMemoryVoiceSessionRepository());
        Map<Integer, java.util.Collection<Integer>> historical = new LinkedHashMap<>();
        historical.put(10, Arrays.<Integer>asList(101, 102));
        coordinator.reconcile(SERVER, historical, TEN, "old-snapshot", 0);

        Map<Integer, java.util.Collection<Integer>> stillOccupied = new LinkedHashMap<>();
        stillOccupied.put(10, Arrays.<Integer>asList(101, 103));
        VoiceRoomSession session = session(coordinator.reconcile(SERVER, stillOccupied, EIGHTEEN,
                "local-with-other", 0, 101), 10);

        assertFalse(session.isStartKnown());
        assertEquals(null, session.getVoiceSessionStart());
    }

    @Test
    public void localBootstrapDropsPersistedKnownStartWhenAnotherUserIsPresent() {
        VoiceSessionCoordinator coordinator = coordinator(new InMemoryVoiceSessionRepository());
        coordinator.join(SERVER, 10, 102, TEN, false, "known-join", 1);

        Map<Integer, java.util.Collection<Integer>> stillOccupied = new LinkedHashMap<>();
        stillOccupied.put(10, Arrays.<Integer>asList(101, 102));
        VoiceRoomSession session = session(coordinator.reconcile(SERVER, stillOccupied, EIGHTEEN,
                "known-with-local", 0, 101), 10);

        assertFalse(session.isStartKnown());
        assertEquals(null, session.getVoiceSessionStart());
        assertEquals(2, session.getPresentUsers().size());
    }

    @Test
    public void newClientUsesExistingPeerMarkerInsteadOfItsPersistedTimer() {
        VoiceSessionCoordinator mac = coordinator(new InMemoryVoiceSessionRepository());
        Map<Integer, java.util.Collection<Integer>> macOnly = new LinkedHashMap<>();
        macOnly.put(10, Collections.<Integer>singleton(101));
        VoiceRoomSession macSession = session(mac.reconcile(SERVER, macOnly, EIGHTEEN,
                "mac-empty-server", 0, 101), 10);

        VoiceSessionCoordinator windows = coordinator(new InMemoryVoiceSessionRepository());
        windows.join(SERVER, 10, 102, TEN, false, "stale-windows-state", 1);
        Map<Integer, java.util.Collection<Integer>> occupied = new LinkedHashMap<>();
        occupied.put(10, Arrays.<Integer>asList(101, 102));
        VoiceRoomSession beforeMarker = session(windows.reconcile(SERVER, occupied, EIGHTEEN,
                "windows-joins-occupied-server", 0, 102), 10);

        assertTrue(macSession.isStartKnown());
        assertEquals(EIGHTEEN, macSession.getVoiceSessionStart());
        assertFalse(beforeMarker.isStartKnown());
        assertEquals(null, beforeMarker.getVoiceSessionStart());

        VoiceRoomSession afterMarker = session(windows.adoptSessionStart(SERVER, 10,
                macSession.getVoiceSessionStart(), "mac-session-marker"), 10);
        assertTrue(afterMarker.isStartKnown());
        assertEquals(EIGHTEEN, afterMarker.getVoiceSessionStart());
        assertEquals(2, afterMarker.getPresentUsers().size());
    }

    @Test
    public void stalePeerMarkerCannotReplaceFreshLocalBootstrap() {
        VoiceSessionCoordinator coordinator = coordinator(new InMemoryVoiceSessionRepository());
        Map<Integer, java.util.Collection<Integer>> localOnly = new LinkedHashMap<>();
        localOnly.put(10, Collections.<Integer>singleton(101));
        coordinator.reconcile(SERVER, localOnly, EIGHTEEN, "fresh-local-bootstrap", 0, 101);

        VoiceRoomSession protectedStart = session(coordinator.adoptSessionStart(SERVER, 10,
                TEN, "stale-peer-marker"), 10);

        assertTrue(protectedStart.isStartKnown());
        assertEquals(EIGHTEEN, protectedStart.getVoiceSessionStart());
        assertEquals(Collections.singleton(101), protectedStart.getPresentUsers());
    }

    @Test
    public void peerMarkerCorrectsARestrictedLocalOnlySnapshot() {
        VoiceSessionCoordinator coordinator = coordinator(new InMemoryVoiceSessionRepository());
        Map<Integer, java.util.Collection<Integer>> localOnly = new LinkedHashMap<>();
        localOnly.put(10, Collections.<Integer>singleton(101));
        coordinator.reconcile(SERVER, localOnly, EIGHTEEN, "fresh-local-bootstrap", 0, 101);

        // A private marker request is evidence that another client is really
        // present even if this identity could not list it in the snapshot.
        coordinator.join(SERVER, 10, 102, EIGHTEEN, true, "peer-request", 0);
        VoiceRoomSession adopted = session(coordinator.adoptSessionStart(SERVER, 10,
                TEN, "peer-marker"), 10);

        assertTrue(adopted.isStartKnown());
        assertEquals(TEN, adopted.getVoiceSessionStart());
        assertEquals(2, adopted.getPresentUsers().size());
        assertTrue(adopted.getPresentUsers().contains(102));
    }

    @Test
    public void restartPreservesAuthoritativeStart() throws Exception {
        Path directory = Files.createTempDirectory("ts3j-session-restart");
        Path statePath = directory.resolve("sessions.db");
        VoiceSessionCoordinator first = coordinator(new FileVoiceSessionRepository(statePath));
        first.join(SERVER, 10, 101, TEN, false, "join-1", 1);

        VoiceSessionCoordinator afterRestart = coordinator(new FileVoiceSessionRepository(statePath));
        VoiceRoomSession restored = session(afterRestart.snapshot(), 10);

        assertEquals(TEN, restored.getVoiceSessionStart());
        assertEquals(Collections.singleton(101), restored.getPresentUsers());
    }

    @Test
    public void twoInstancesShareTheSameSessionFile() throws Exception {
        Path directory = Files.createTempDirectory("ts3j-session-sync");
        Path statePath = directory.resolve("shared.db");
        VoiceSessionCoordinator first = coordinator(new FileVoiceSessionRepository(statePath));
        VoiceSessionCoordinator second = coordinator(new FileVoiceSessionRepository(statePath));

        first.join(SERVER, 10, 101, TEN, false, "instance-a", 1);
        second.join(SERVER, 10, 102, EIGHTEEN, false, "instance-b", 1);

        VoiceRoomSession fromFirst = session(first.snapshot(), 10);
        VoiceRoomSession fromSecond = session(second.snapshot(), 10);
        assertEquals(TEN, fromFirst.getVoiceSessionStart());
        assertEquals(TEN, fromSecond.getVoiceSessionStart());
        assertEquals(2, fromFirst.getPresentUsers().size());
        assertEquals(fromFirst.getPresentUsers(), fromSecond.getPresentUsers());
    }

    @Test
    public void anAuthoritativeMarkerPromotesAnOccupiedUnknownSession() {
        VoiceSessionCoordinator coordinator = coordinator(new InMemoryVoiceSessionRepository());
        Map<Integer, java.util.Collection<Integer>> occupied = new LinkedHashMap<>();
        occupied.put(10, Arrays.<Integer>asList(101, 102));
        coordinator.reconcile(SERVER, occupied, EIGHTEEN, "snapshot", 0);

        VoiceRoomSession adopted = session(coordinator.adoptSessionStart(
                SERVER, 10, TEN, "remote-marker"), 10);

        assertTrue(adopted.isStartKnown());
        assertEquals(TEN, adopted.getVoiceSessionStart());
        assertEquals(2, adopted.getPresentUsers().size());
    }

    @Test
    public void aStaleMarkerCannotResurrectAnEmptyChannel() {
        VoiceSessionCoordinator coordinator = coordinator(new InMemoryVoiceSessionRepository());
        coordinator.join(SERVER, 10, 101, TEN, false, "join", 1);
        coordinator.leave(SERVER, 10, 101, EIGHTEEN, "leave", 2);

        assertTrue(coordinator.adoptSessionStart(SERVER, 10, TEN, "late-marker")
                .getSessions().isEmpty());
    }

    @Test
    public void reconnectionSnapshotRemovesUsersWhoLeftWhileOffline() throws Exception {
        Path directory = Files.createTempDirectory("ts3j-session-reconnect");
        VoiceSessionCoordinator coordinator = coordinator(new FileVoiceSessionRepository(
                directory.resolve("shared.db")));
        coordinator.join(SERVER, 10, 101, TEN, false, "join-1", 1);
        coordinator.join(SERVER, 10, 102, EIGHTEEN, false, "join-2", 1);

        Map<Integer, java.util.Collection<Integer>> afterReconnect = new LinkedHashMap<>();
        afterReconnect.put(10, Collections.<Integer>singleton(102));
        VoiceRoomSession restored = session(coordinator.reconcile(SERVER, afterReconnect,
                Instant.parse("2026-01-02T10:00:00Z"), "reconnect-1", 0), 10);

        assertEquals(TEN, restored.getVoiceSessionStart());
        assertEquals(Collections.singleton(102), restored.getPresentUsers());
    }
}
