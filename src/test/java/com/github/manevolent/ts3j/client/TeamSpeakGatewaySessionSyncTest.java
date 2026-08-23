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
}
