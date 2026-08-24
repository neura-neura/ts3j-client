package com.github.manevolent.ts3j.client;

import com.github.manevolent.ts3j.event.TextMessageEvent;
import com.github.manevolent.ts3j.protocol.socket.client.LocalTeamspeakClientSocket;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TeamSpeakGatewaySessionSyncTest {
    private static final String SERVER = "voice.example:9987";
    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-01-01T18:00:00Z");

    @Test
    public void privateSessionMarkerPromotesAnOccupiedUnknownSession() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        VoiceSessionCoordinator coordinator = new VoiceSessionCoordinator(
                new InMemoryVoiceSessionRepository(), clock);
        TeamSpeakGateway gateway = new TeamSpeakGateway(coordinator, clock);
        try {
            Map<Integer, java.util.Collection<Integer>> occupied = new HashMap<>();
            occupied.put(1, Arrays.<Integer>asList(7, 8));
            coordinator.reconcile(SERVER, occupied, NOW, "snapshot", 0);

            java.lang.reflect.Field config = TeamSpeakGateway.class.getDeclaredField("config");
            config.setAccessible(true);
            config.set(gateway, new ConnectionConfig("voice.example", 9987, "", "tester", null));

            String encodedServer = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    SERVER.getBytes(StandardCharsets.UTF_8));
            Map<String, String> event = new HashMap<>();
            event.put("targetmode", "1");
            event.put("invokerid", "7");
            event.put("msg", "ts3j-session-v1|" + encodedServer + "|1|" + START);
            gateway.onTextMessage(new TextMessageEvent(event));

            VoiceRoomSession session = coordinator.snapshot().get(new SessionKey(SERVER, 1));
            assertTrue(session.isStartKnown());
            assertEquals(START, session.getVoiceSessionStart());
        } finally {
            gateway.close();
        }
    }

    @Test
    public void markerReceivedDuringInitialSyncWaitsForFreshSnapshot() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        VoiceSessionCoordinator coordinator = new VoiceSessionCoordinator(
                new InMemoryVoiceSessionRepository(), clock);
        TeamSpeakGateway gateway = new TeamSpeakGateway(coordinator, clock);
        try {
            // Simulate the stale timer left by this computer's previous run.
            coordinator.join(SERVER, 1, 8, START, false, "stale-local-start", 1);

            java.lang.reflect.Field config = TeamSpeakGateway.class.getDeclaredField("config");
            config.setAccessible(true);
            config.set(gateway, new ConnectionConfig("voice.example", 9987, "", "tester", null));
            java.lang.reflect.Field initialSync = TeamSpeakGateway.class
                    .getDeclaredField("initialSync");
            initialSync.setAccessible(true);
            initialSync.setBoolean(gateway, true);
            java.lang.reflect.Field stateReady = TeamSpeakGateway.class
                    .getDeclaredField("sessionStateReady");
            stateReady.setAccessible(true);
            stateReady.setBoolean(gateway, false);

            String encodedServer = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    SERVER.getBytes(StandardCharsets.UTF_8));
            Map<String, String> event = new HashMap<>();
            event.put("targetmode", "1");
            event.put("invokerid", "7");
            event.put("msg", "ts3j-session-v1|" + encodedServer + "|1|" + NOW);
            gateway.onTextMessage(new TextMessageEvent(event));

            // The marker is queued while the persisted timer is still visible.
            assertEquals(START, coordinator.snapshot()
                    .get(new SessionKey(SERVER, 1)).getVoiceSessionStart());

            Map<Integer, java.util.Collection<Integer>> occupied = new HashMap<>();
            occupied.put(1, Arrays.<Integer>asList(7, 8));
            coordinator.reconcile(SERVER, occupied, NOW, "fresh-snapshot", 0, 8);

            java.lang.reflect.Method applyPending = TeamSpeakGateway.class
                    .getDeclaredMethod("applyPendingSessionMarkers");
            applyPending.setAccessible(true);
            stateReady.setBoolean(gateway, true);
            initialSync.setBoolean(gateway, false);
            applyPending.invoke(gateway);

            VoiceRoomSession adopted = coordinator.snapshot().get(new SessionKey(SERVER, 1));
            assertTrue(adopted.isStartKnown());
            assertEquals(NOW, adopted.getVoiceSessionStart());
        } finally {
            gateway.close();
        }
    }

    @Test
    public void staleMarkerCannotOverwriteFreshEmptyServerStart() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        VoiceSessionCoordinator coordinator = new VoiceSessionCoordinator(
                new InMemoryVoiceSessionRepository(), clock);
        TeamSpeakGateway gateway = new TeamSpeakGateway(coordinator, clock);
        try {
            Map<Integer, java.util.Collection<Integer>> localOnly = new HashMap<>();
            localOnly.put(1, java.util.Collections.<Integer>singleton(8));
            coordinator.reconcile(SERVER, localOnly, NOW, "empty-server-local", 0, 8);

            java.lang.reflect.Field config = TeamSpeakGateway.class.getDeclaredField("config");
            config.setAccessible(true);
            config.set(gateway, new ConnectionConfig("voice.example", 9987, "", "tester", null));

            String encodedServer = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    SERVER.getBytes(StandardCharsets.UTF_8));
            Map<String, String> event = new HashMap<>();
            event.put("targetmode", "1");
            event.put("invokerid", "7");
            event.put("msg", "ts3j-session-v1|" + encodedServer + "|1|" + START);
            gateway.onTextMessage(new TextMessageEvent(event));

            VoiceRoomSession session = coordinator.snapshot().get(new SessionKey(SERVER, 1));
            assertTrue(session.isStartKnown());
            assertEquals(NOW, session.getVoiceSessionStart());
        } finally {
            gateway.close();
        }
    }

    @Test
    public void peerMarkerIsSentEvenWhenPeerMetadataCannotBeRead() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        VoiceSessionCoordinator coordinator = new VoiceSessionCoordinator(
                new InMemoryVoiceSessionRepository(), clock);
        TeamSpeakGateway gateway = new TeamSpeakGateway(coordinator, clock);
        RecordingSocket socket = new RecordingSocket();
        socket.setClientId(8);
        try {
            java.lang.reflect.Field config = TeamSpeakGateway.class.getDeclaredField("config");
            config.setAccessible(true);
            config.set(gateway, new ConnectionConfig("voice.example", 9987, "", "tester", null));
            java.lang.reflect.Field socketField = TeamSpeakGateway.class.getDeclaredField("socket");
            socketField.setAccessible(true);
            socketField.set(gateway, socket);

            Map<Integer, java.util.Collection<Integer>> occupied = new HashMap<>();
            occupied.put(1, Arrays.<Integer>asList(8));
            coordinator.reconcile(SERVER, occupied, NOW, "local-bootstrap", 0, 8);

            java.lang.reflect.Method queue = TeamSpeakGateway.class.getDeclaredMethod(
                    "queuePeerSessionMarker", LocalTeamspeakClientSocket.class,
                    int.class, int.class);
            queue.setAccessible(true);
            queue.invoke(gateway, socket, 7, 1);

            long deadline = System.nanoTime() + 1_000_000_000L;
            while (socket.messages.size() < 2 && System.nanoTime() < deadline) {
                Thread.sleep(10L);
            }
            assertTrue(socket.messages.toString(), socket.messages.stream()
                    .anyMatch(message -> message.startsWith("ts3j-session-request-v1|")));
            assertTrue(socket.messages.toString(), socket.messages.stream()
                    .anyMatch(message -> message.startsWith("ts3j-session-v1|")));
        } finally {
            gateway.close();
        }
    }

    @Test
    public void privateRequestMarksHiddenPeerBeforeItsEarlierMarkerArrives() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        VoiceSessionCoordinator coordinator = new VoiceSessionCoordinator(
                new InMemoryVoiceSessionRepository(), clock);
        TeamSpeakGateway gateway = new TeamSpeakGateway(coordinator, clock);
        RecordingSocket socket = new RecordingSocket();
        socket.setClientId(8);
        try {
            java.lang.reflect.Field config = TeamSpeakGateway.class.getDeclaredField("config");
            config.setAccessible(true);
            config.set(gateway, new ConnectionConfig("voice.example", 9987, "", "tester", null));
            java.lang.reflect.Field socketField = TeamSpeakGateway.class.getDeclaredField("socket");
            socketField.setAccessible(true);
            socketField.set(gateway, socket);

            Map<Integer, java.util.Collection<Integer>> localOnly = new HashMap<>();
            localOnly.put(1, Arrays.<Integer>asList(8));
            coordinator.reconcile(SERVER, localOnly, NOW, "local-bootstrap", 0, 8);

            String encodedServer = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    SERVER.getBytes(StandardCharsets.UTF_8));
            Map<String, String> request = new HashMap<>();
            request.put("targetmode", "1");
            request.put("invokerid", "7");
            request.put("msg", "ts3j-session-request-v1|" + encodedServer + "|1");
            gateway.onTextMessage(new TextMessageEvent(request));

            Map<String, String> marker = new HashMap<>();
            marker.put("targetmode", "1");
            marker.put("invokerid", "7");
            marker.put("msg", "ts3j-session-v1|" + encodedServer + "|1|" + START);
            gateway.onTextMessage(new TextMessageEvent(marker));

            VoiceRoomSession session = coordinator.snapshot().get(new SessionKey(SERVER, 1));
            assertTrue(session.isStartKnown());
            assertEquals(START, session.getVoiceSessionStart());
            assertTrue(session.getPresentUsers().contains(7));
        } finally {
            gateway.close();
        }
    }

    @Test
    public void channelMarkerReceivedBeforeReconnectSnapshotKeepsTheSharedStart() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        VoiceSessionCoordinator coordinator = new VoiceSessionCoordinator(
                new InMemoryVoiceSessionRepository(), clock);
        TeamSpeakGateway gateway = new TeamSpeakGateway(coordinator, clock);
        try {
            java.lang.reflect.Field config = TeamSpeakGateway.class.getDeclaredField("config");
            config.setAccessible(true);
            config.set(gateway, new ConnectionConfig("voice.example", 9987, "", "tester", null));
            java.lang.reflect.Field initialSync = TeamSpeakGateway.class
                    .getDeclaredField("initialSync");
            initialSync.setAccessible(true);
            initialSync.setBoolean(gateway, true);
            java.lang.reflect.Field stateReady = TeamSpeakGateway.class
                    .getDeclaredField("sessionStateReady");
            stateReady.setAccessible(true);
            stateReady.setBoolean(gateway, false);

            String encodedServer = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    SERVER.getBytes(StandardCharsets.UTF_8));
            Map<String, String> marker = new HashMap<>();
            marker.put("targetmode", "2");
            marker.put("target", "1");
            marker.put("invokerid", "7");
            marker.put("msg", "ts3j-session-v1|" + encodedServer + "|1|" + START);
            // The peer's marker can arrive before this reconnect's restricted
            // client snapshot, which only contains the local client (8).
            gateway.onTextMessage(new TextMessageEvent(marker));

            Map<Integer, java.util.Collection<Integer>> localOnly = new HashMap<>();
            localOnly.put(1, Arrays.<Integer>asList(8));
            coordinator.reconcile(SERVER, localOnly, NOW, "reconnect-snapshot", 0, 8);

            java.lang.reflect.Method applyPending = TeamSpeakGateway.class
                    .getDeclaredMethod("applyPendingSessionMarkers");
            applyPending.setAccessible(true);
            stateReady.setBoolean(gateway, true);
            initialSync.setBoolean(gateway, false);
            applyPending.invoke(gateway);

            VoiceRoomSession synchronizedSession = coordinator.snapshot()
                    .get(new SessionKey(SERVER, 1));
            assertTrue(synchronizedSession.isStartKnown());
            assertEquals(START, synchronizedSession.getVoiceSessionStart());
            assertTrue(synchronizedSession.getPresentUsers().contains(7));
            assertTrue(synchronizedSession.getPresentUsers().contains(8));
        } finally {
            gateway.close();
        }
    }

    @Test
    public void channelBroadcastSynchronizesARestrictedPeerWithoutClientIds() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        VoiceSessionCoordinator macSessions = new VoiceSessionCoordinator(
                new InMemoryVoiceSessionRepository(), clock);
        VoiceSessionCoordinator windowsSessions = new VoiceSessionCoordinator(
                new InMemoryVoiceSessionRepository(), clock);
        TeamSpeakGateway mac = new TeamSpeakGateway(macSessions, clock);
        TeamSpeakGateway windows = new TeamSpeakGateway(windowsSessions, clock);
        RecordingSocket macSocket = new RecordingSocket();
        RecordingSocket windowsSocket = new RecordingSocket();
        macSocket.setClientId(7);
        windowsSocket.setClientId(8);
        try {
            configure(mac, macSocket);
            configure(windows, windowsSocket);

            Map<Integer, java.util.Collection<Integer>> macOnly = new HashMap<>();
            macOnly.put(1, Arrays.<Integer>asList(7));
            macSessions.reconcile(SERVER, macOnly, START, "mac-bootstrap", 0, 7);

            // Windows can see its own occupied channel but not the peer's id.
            Map<Integer, java.util.Collection<Integer>> windowsOnly = new HashMap<>();
            windowsOnly.put(1, Arrays.<Integer>asList(8));
            windowsSessions.reconcile(SERVER, windowsOnly, NOW, "windows-bootstrap", 0, 8);

            java.lang.reflect.Method request = TeamSpeakGateway.class.getDeclaredMethod(
                    "queueChannelSessionMarkerRequest", LocalTeamspeakClientSocket.class,
                    int.class);
            request.setAccessible(true);
            request.invoke(windows, windowsSocket, 1);

            String channelRequest = awaitMessage(windowsSocket, "ts3j-session-channel-request-v1|");
            Map<String, String> requestEvent = new HashMap<>();
            requestEvent.put("targetmode", "2");
            requestEvent.put("target", "1");
            requestEvent.put("invokerid", "8");
            requestEvent.put("msg", channelRequest);
            mac.onTextMessage(new TextMessageEvent(requestEvent));

            String marker = awaitMessage(macSocket, "ts3j-session-v1|");
            Map<String, String> markerEvent = new HashMap<>();
            markerEvent.put("targetmode", "2");
            markerEvent.put("target", "1");
            markerEvent.put("invokerid", "7");
            markerEvent.put("msg", marker);
            windows.onTextMessage(new TextMessageEvent(markerEvent));

            VoiceRoomSession synchronizedSession = windowsSessions.snapshot()
                    .get(new SessionKey(SERVER, 1));
            assertTrue(synchronizedSession.isStartKnown());
            assertEquals(START, synchronizedSession.getVoiceSessionStart());
            assertTrue(synchronizedSession.getPresentUsers().contains(7));
            assertTrue(synchronizedSession.getPresentUsers().contains(8));
        } finally {
            windows.close();
            mac.close();
        }
    }

    @Test
    public void channelMarkerRequestIsSentAgainAfterARejoin() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        VoiceSessionCoordinator coordinator = new VoiceSessionCoordinator(
                new InMemoryVoiceSessionRepository(), clock);
        TeamSpeakGateway gateway = new TeamSpeakGateway(coordinator, clock);
        RecordingSocket socket = new RecordingSocket();
        socket.setClientId(8);
        try {
            configure(gateway, socket);
            java.lang.reflect.Method request = TeamSpeakGateway.class.getDeclaredMethod(
                    "queueChannelSessionMarkerRequest", LocalTeamspeakClientSocket.class,
                    int.class);
            request.setAccessible(true);
            request.invoke(gateway, socket, 1);
            request.invoke(gateway, socket, 1);

            long deadline = System.nanoTime() + 1_000_000_000L;
            while (System.nanoTime() < deadline) {
                synchronized (socket.messages) {
                    long count = socket.messages.stream()
                            .filter(message -> message.startsWith("ts3j-session-channel-request-v1|"))
                            .count();
                    if (count >= 2) return;
                }
                Thread.sleep(10L);
            }
            throw new AssertionError("The rejoin request was suppressed: " + socket.messages);
        } finally {
            gateway.close();
        }
    }

    private static void configure(TeamSpeakGateway gateway, RecordingSocket socket)
            throws Exception {
        java.lang.reflect.Field config = TeamSpeakGateway.class.getDeclaredField("config");
        config.setAccessible(true);
        config.set(gateway, new ConnectionConfig("voice.example", 9987, "", "tester", null));
        java.lang.reflect.Field socketField = TeamSpeakGateway.class.getDeclaredField("socket");
        socketField.setAccessible(true);
        socketField.set(gateway, socket);
    }

    private static String awaitMessage(RecordingSocket socket, String prefix)
            throws Exception {
        long deadline = System.nanoTime() + 1_000_000_000L;
        while (System.nanoTime() < deadline) {
            synchronized (socket.messages) {
                for (String message : socket.messages) {
                    if (message.startsWith(prefix)) return message;
                }
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("Timed out waiting for " + prefix + ": " + socket.messages);
    }

    private static final class RecordingSocket extends LocalTeamspeakClientSocket {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void sendPrivateMessage(int clientId, String message) {
            synchronized (messages) {
                messages.add(message);
            }
        }

        @Override
        public void sendChannelMessage(int channelId, String message) {
            synchronized (messages) {
                messages.add(message);
            }
        }
    }
}
