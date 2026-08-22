package com.github.manevolent.ts3j.client;

import com.github.manevolent.ts3j.event.ClientJoinEvent;
import com.github.manevolent.ts3j.event.ClientUpdatedEvent;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TeamSpeakGatewayClientControlsTest {
    @Test
    public void clientUpdateEventsPreserveUnchangedFlags() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T12:00:00Z"), ZoneOffset.UTC);
        TeamSpeakGateway gateway = new TeamSpeakGateway(
                new VoiceSessionCoordinator(new InMemoryVoiceSessionRepository(), clock), clock,
                new InMemoryChannelChatRepository());
        try {
            Map<String, String> joined = new HashMap<>();
            joined.put("clid", "7");
            joined.put("cid", "42");
            joined.put("ctid", "42");
            joined.put("client_nickname", "neura");
            joined.put("client_type", "0");
            joined.put("client_input_muted", "0");
            joined.put("client_output_muted", "1");
            joined.put("client_away", "0");
            gateway.onClientJoin(new ClientJoinEvent(joined));

            Map<String, String> updated = new HashMap<>();
            updated.put("clid", "7");
            updated.put("client_input_muted", "1");
            updated.put("client_away", "1");
            gateway.onClientChanged(new ClientUpdatedEvent(updated));

            ClientView local = gateway.snapshot().getClients().get(7);
            assertTrue(local.isInputMuted());
            assertTrue(local.isOutputMuted());
            assertTrue(local.isAway());
        } finally {
            gateway.close();
        }
    }

    @Test
    public void clientControlCapabilitiesAreAvailableThroughClientUpdate() {
        TeamSpeakGateway gateway = new TeamSpeakGateway(
                new VoiceSessionCoordinator(new InMemoryVoiceSessionRepository()));
        try {
            assertTrue(gateway.supportsAwayStatus());
            assertTrue(gateway.supportsMicrophoneMute());
            assertTrue(gateway.supportsAudioMute());
            assertFalse(gateway.snapshot().getClients().containsKey(0));
        } finally {
            gateway.close();
        }
    }
}
