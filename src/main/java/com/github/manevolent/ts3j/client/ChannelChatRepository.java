package com.github.manevolent.ts3j.client;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Local persistence boundary for channel chat history. */
interface ChannelChatRepository {
    Map<Integer, List<ChannelTextMessage>> load(String serverId) throws IOException;

    void append(String serverId, ChannelTextMessage message) throws IOException;
}
