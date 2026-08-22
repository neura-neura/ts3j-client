package com.github.manevolent.ts3j.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small deterministic repository used by tests and the demo boundary. */
final class InMemoryChannelChatRepository implements ChannelChatRepository {
    private final Map<String, Map<Integer, List<ChannelTextMessage>>> histories = new HashMap<>();

    @Override
    public synchronized Map<Integer, List<ChannelTextMessage>> load(String serverId) throws IOException {
        Map<Integer, List<ChannelTextMessage>> source = histories.get(serverId);
        if (source == null) return Collections.emptyMap();
        Map<Integer, List<ChannelTextMessage>> copy = new HashMap<>();
        for (Map.Entry<Integer, List<ChannelTextMessage>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return copy;
    }

    @Override
    public synchronized void append(String serverId, ChannelTextMessage message) throws IOException {
        Map<Integer, List<ChannelTextMessage>> byChannel = histories.get(serverId);
        if (byChannel == null) {
            byChannel = new HashMap<>();
            histories.put(serverId, byChannel);
        }
        List<ChannelTextMessage> messages = byChannel.get(message.getChannelId());
        if (messages == null) {
            messages = new ArrayList<>();
            byChannel.put(message.getChannelId(), messages);
        }
        messages.add(message);
    }
}
