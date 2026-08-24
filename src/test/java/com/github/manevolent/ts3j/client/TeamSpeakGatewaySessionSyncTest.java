package com.github.manevolent.ts3j.client;

import com.github.manevolent.ts3j.event.TextMessageEvent;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
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
}
