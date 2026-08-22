package com.github.manevolent.ts3j.client;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explicit local preview data used only with the --demo flag. */
final class DemoData {
    private DemoData() { }

    static GatewaySnapshot snapshot() {
        String server = "demo:9987";
        Instant now = Clock.systemUTC().instant();
        InMemoryVoiceSessionRepository repository = new InMemoryVoiceSessionRepository();
        VoiceSessionCoordinator coordinator = new VoiceSessionCoordinator(repository,
                Clock.systemUTC());
        coordinator.join(server, 2, 101, now.minus(Duration.ofHours(8)), false,
                "demo-a", 1L);
        coordinator.join(server, 2, 102, now.minus(Duration.ofHours(7)), false,
                "demo-b", 1L);
        coordinator.join(server, 3, 103, now.minus(Duration.ofMinutes(18)), false,
                "demo-c", 1L);

        Map<Integer, ChannelView> channels = new LinkedHashMap<>();
        channels.put(1, new ChannelView(1, 0, 1, "Conversación", false, "TEXT"));
        channels.put(2, new ChannelView(2, 0, 2, "Sala principal", true, "OPUS_VOICE"));
        channels.put(3, new ChannelView(3, 0, 3, "Estudio", true, "OPUS_MUSIC"));

        Map<Integer, ClientView> clients = new LinkedHashMap<>();
        clients.put(101, new ClientView(101, 2, "Ariadna", 0, false, false));
        clients.put(102, new ClientView(102, 2, "Bruno", 0, true, false));
        clients.put(103, new ClientView(103, 3, "Carmen", 0, false, false));

        List<ChannelTextMessage> chat = Arrays.asList(
                new ChannelTextMessage(1, "neura", "Bienvenidos al canal.", now.minus(Duration.ofHours(2))),
                new ChannelTextMessage(1, "neura", "El historial se conserva entre conexiones.",
                        now.minus(Duration.ofHours(2)).plusSeconds(45)),
                new ChannelTextMessage(1, "neura2", "Hola, ya estoy aquí.", now.minus(Duration.ofMinutes(8))),
                new ChannelTextMessage(1, "neura", "Perfecto, te leo.", now.minus(Duration.ofMinutes(4))));
        Map<Integer, List<ChannelTextMessage>> messages = new LinkedHashMap<>();
        messages.put(1, chat);
        Map<Integer, Integer> historyBoundaries = Collections.singletonMap(1, 2);

        return new GatewaySnapshot(ConnectionStatus.CONNECTED_IN_CHANNEL, server, "",
                101, 2, channels, clients, coordinator.snapshot(), messages,
                historyBoundaries, true);
    }
}
