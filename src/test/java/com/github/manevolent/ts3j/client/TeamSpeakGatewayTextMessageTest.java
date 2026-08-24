package com.github.manevolent.ts3j.client;

import com.github.manevolent.ts3j.event.TextMessageEvent;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TeamSpeakGatewayTextMessageTest {
    @Test
    public void channelMessageFromVoiceChannelIsPublished() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC);
        TeamSpeakGateway gateway = new TeamSpeakGateway(
                new VoiceSessionCoordinator(new InMemoryVoiceSessionRepository(), clock), clock);
        try {
            Map<String, String> eventMap = new HashMap<>();
            eventMap.put("targetmode", "2");
            eventMap.put("target", "42");
            eventMap.put("invokerid", "7");
            eventMap.put("invokername", "neura2");
            eventMap.put("msg", "Hola desde el canal de voz");

            gateway.onTextMessage(new TextMessageEvent(eventMap));

            List<ChannelTextMessage> messages = gateway.snapshot().getChannelMessages().get(42);
            assertNotNull(messages);
            assertEquals(1, messages.size());
            assertEquals("neura2", messages.get(0).getSender());
            assertEquals("Hola desde el canal de voz", messages.get(0).getMessage());
            assertEquals(Instant.parse("2026-08-21T12:00:00Z"), messages.get(0).getReceivedAt());
        } finally {
            gateway.close();
        }
    }

    @Test
    public void fullClientChannelEventCanExposeSubscriptionChannel() {
        Map<String, String> eventMap = new HashMap<>();
        eventMap.put("targetmode", "2");
        eventMap.put("__cmd_listener_channel_id", "42");
        eventMap.put("invokerid", "7");
        eventMap.put("invokername", "neura2");
        eventMap.put("msg", "Mensaje recibido desde la suscripción");

        TextMessageEvent event = new TextMessageEvent(eventMap);

        assertEquals(42, event.getTargetChannelId());
    }

    @Test
    public void channelEventWithoutTargetUsesTheSubscribedLocalChannel() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC);
        TeamSpeakGateway gateway = new TeamSpeakGateway(
                new VoiceSessionCoordinator(new InMemoryVoiceSessionRepository(), clock), clock);
        try {
            Field currentChannel = TeamSpeakGateway.class.getDeclaredField("currentChannelId");
            currentChannel.setAccessible(true);
            currentChannel.setInt(gateway, 42);

            Map<String, String> eventMap = new HashMap<>();
            eventMap.put("targetmode", "2");
            eventMap.put("invokerid", "7");
            eventMap.put("invokername", "neura2");
            eventMap.put("msg", "Evento sin target explícito");
            gateway.onTextMessage(new TextMessageEvent(eventMap));

            assertEquals(1, gateway.snapshot().getChannelMessages().get(42).size());
        } finally {
            gateway.close();
        }
    }

    @Test
    public void serverAuthorityMarkerReplacesLocalStartWithoutAddingQueryBot() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC);
        VoiceSessionCoordinator coordinator = new VoiceSessionCoordinator(
                new InMemoryVoiceSessionRepository(), clock);
        TeamSpeakGateway gateway = new TeamSpeakGateway(coordinator, clock);
        try {
            Field config = TeamSpeakGateway.class.getDeclaredField("config");
            config.setAccessible(true);
            config.set(gateway, new ConnectionConfig("server", 9987, "", "neura", null));

            coordinator.join("server:9987", 42, 1, clock.instant(), false, "local", 0L);
            String encodedServer = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("server:9987".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Map<String, String> eventMap = new HashMap<>();
            eventMap.put("targetmode", "1");
            eventMap.put("target", "1");
            eventMap.put("invokerid", "7");
            eventMap.put("msg", "ts3j-server-session-v1|" + encodedServer
                    + "|42|server-session|2026-08-21T11:00:00Z|2026-08-21T12:00:00Z");

            gateway.onTextMessage(new TextMessageEvent(eventMap));

            VoiceRoomSession session = gateway.snapshot().getSessions()
                    .get(new SessionKey("server:9987", 42));
            assertEquals(Instant.parse("2026-08-21T11:00:00Z"), session.getVoiceSessionStart());
            assertEquals(1, session.getPresentUsers().size());
        } finally {
            gateway.close();
        }
    }

    @Test
    public void serverMarkerCompensatesAThreeSecondLocalClockSkew() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T12:00:03Z"), ZoneOffset.UTC);
        VoiceSessionCoordinator coordinator = new VoiceSessionCoordinator(
                new InMemoryVoiceSessionRepository(), clock);
        TeamSpeakGateway gateway = new TeamSpeakGateway(coordinator, clock);
        try {
            Field config = TeamSpeakGateway.class.getDeclaredField("config");
            config.setAccessible(true);
            config.set(gateway, new ConnectionConfig("server", 9987, "", "neura", null));

            coordinator.join("server:9987", 42, 1, clock.instant(), true, "local", 0L);
            String encodedServer = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("server:9987".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Map<String, String> eventMap = new HashMap<>();
            eventMap.put("targetmode", "2");
            eventMap.put("target", "42");
            eventMap.put("invokerid", "7");
            eventMap.put("msg", "ts3j-server-session-v1|" + encodedServer
                    + "|42|server-session|2026-08-21T11:00:00Z|2026-08-21T12:00:00Z");

            gateway.onTextMessage(new TextMessageEvent(eventMap));

            VoiceRoomSession session = gateway.snapshot().getSessions()
                    .get(new SessionKey("server:9987", 42));
            assertEquals(Instant.parse("2026-08-21T11:00:03Z"),
                    session.getVoiceSessionStart());
            assertEquals("01:00:00", SessionTimerPresentation.value(session, clock.instant()));
        } finally {
            gateway.close();
        }
    }
}
