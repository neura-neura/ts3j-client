package com.github.manevolent.ts3j.client;

import com.github.manevolent.ts3j.event.ClientJoinEvent;
import com.github.manevolent.ts3j.event.ClientLeaveEvent;
import com.github.manevolent.ts3j.event.ClientUpdatedEvent;
import com.github.manevolent.ts3j.protocol.socket.client.LocalTeamspeakClientSocket;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TeamSpeakActivityTest {
    @Test
    public void remoteJoinAndLeaveInTheCurrentChannelBecomeActivities() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC);
        TeamSpeakGateway gateway = new TeamSpeakGateway(
                new VoiceSessionCoordinator(new InMemoryVoiceSessionRepository(), clock), clock,
                new InMemoryChannelChatRepository());
        List<TeamSpeakActivity> activities = new ArrayList<>();
        gateway.addActivityListener(activities::add);
        setInt(gateway, "currentChannelId", 42);
        try {
            Map<String, String> joined = clientMap(7, 42, "neura2");
            gateway.onClientJoin(new ClientJoinEvent(joined));
            gateway.onClientLeave(new ClientLeaveEvent(joined));

            assertEquals(2, activities.size());
            assertEquals(TeamSpeakActivity.Type.CLIENT_JOINED_CURRENT_CHANNEL,
                    activities.get(0).getType());
            assertEquals(TeamSpeakActivity.Type.CLIENT_LEFT_CURRENT_CHANNEL,
                    activities.get(1).getType());
        } finally {
            gateway.close();
        }
    }

    @Test
    public void localMuteTransitionIsReportedOnlyWhenTheFlagChanges() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC);
        TeamSpeakGateway gateway = new TeamSpeakGateway(
                new VoiceSessionCoordinator(new InMemoryVoiceSessionRepository(), clock), clock,
                new InMemoryChannelChatRepository());
        List<TeamSpeakActivity> activities = new ArrayList<>();
        gateway.addActivityListener(activities::add);
        LocalTeamspeakClientSocket socket = new LocalTeamspeakClientSocket();
        socket.setClientId(7);
        setField(gateway, "socket", socket);
        try {
            gateway.onClientJoin(new ClientJoinEvent(clientMap(7, 42, "neura")));
            Map<String, String> changed = new HashMap<>();
            changed.put("clid", "7");
            changed.put("client_input_muted", "1");
            gateway.onClientChanged(new ClientUpdatedEvent(changed));
            gateway.onClientChanged(new ClientUpdatedEvent(changed));

            assertEquals(1, activities.size());
            assertEquals(TeamSpeakActivity.Type.MICROPHONE_MUTED, activities.get(0).getType());
            assertTrue(gateway.snapshot().getClients().get(7).isInputMuted());
        } finally {
            gateway.close();
        }
    }

    @Test
    public void outboundChatActivityIsNotDeduplicatedAcrossQuickSends() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC);
        TeamSpeakGateway gateway = new TeamSpeakGateway(
                new VoiceSessionCoordinator(new InMemoryVoiceSessionRepository(), clock), clock,
                new InMemoryChannelChatRepository());
        List<TeamSpeakActivity> activities = new ArrayList<>();
        gateway.addActivityListener(activities::add);
        Method emitActivity = TeamSpeakGateway.class.getDeclaredMethod(
                "emitActivity", TeamSpeakActivity.class);
        emitActivity.setAccessible(true);
        TeamSpeakActivity sent = new TeamSpeakActivity(TeamSpeakActivity.Type.CHAT_SENT,
                "neura", "", "General", "server");
        try {
            emitActivity.invoke(gateway, sent);
            emitActivity.invoke(gateway, sent);

            assertEquals(2, activities.size());
            assertEquals(TeamSpeakActivity.Type.CHAT_SENT, activities.get(0).getType());
            assertEquals(TeamSpeakActivity.Type.CHAT_SENT, activities.get(1).getType());
        } finally {
            gateway.close();
        }
    }

    private static Map<String, String> clientMap(int id, int channelId, String nickname) {
        Map<String, String> map = new HashMap<>();
        map.put("clid", Integer.toString(id));
        map.put("cid", Integer.toString(channelId));
        map.put("ctid", Integer.toString(channelId));
        map.put("cfid", Integer.toString(channelId));
        map.put("client_nickname", nickname);
        map.put("client_type", "0");
        map.put("client_input_muted", "0");
        map.put("client_output_muted", "0");
        map.put("client_away", "0");
        return map;
    }

    private static void setInt(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
