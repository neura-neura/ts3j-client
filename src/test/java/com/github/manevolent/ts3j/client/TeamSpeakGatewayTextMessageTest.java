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
}
