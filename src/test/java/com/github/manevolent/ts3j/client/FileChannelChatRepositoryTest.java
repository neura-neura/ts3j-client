package com.github.manevolent.ts3j.client;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FileChannelChatRepositoryTest {
    @Test
    public void persistsUnicodeTabsAndNewlinesAcrossRepositoryInstances() throws Exception {
        Path directory = Files.createTempDirectory("ts3j-chat-history");
        FileChannelChatRepository first = new FileChannelChatRepository(directory);
        Instant receivedAt = Instant.parse("2026-08-21T16:20:30.123456789Z");
        first.append("192.168.196.65:9987", new ChannelTextMessage(
                1, "neura ⚡", "línea uno\tlínea dos\ncon salto", receivedAt));

        Map<Integer, List<ChannelTextMessage>> restored =
                new FileChannelChatRepository(directory).load("192.168.196.65:9987");
        assertNotNull(restored.get(1));
        assertEquals(1, restored.get(1).size());
        assertEquals("neura ⚡", restored.get(1).get(0).getSender());
        assertEquals("línea uno\tlínea dos\ncon salto", restored.get(1).get(0).getMessage());
        assertEquals(receivedAt, restored.get(1).get(0).getReceivedAt());
    }

    @Test
    public void keepsOnlyRecentMessagesPerChannelWhenLoading() throws Exception {
        Path directory = Files.createTempDirectory("ts3j-chat-history-cap");
        FileChannelChatRepository repository = new FileChannelChatRepository(directory);
        for (int i = 0; i < 105; i++) {
            repository.append("server:9987", new ChannelTextMessage(
                    4, "user", "message-" + i, Instant.ofEpochSecond(i)));
        }

        List<ChannelTextMessage> restored = new FileChannelChatRepository(directory)
                .load("server:9987").get(4);
        assertEquals(100, restored.size());
        assertEquals("message-5", restored.get(0).getMessage());
        assertEquals("message-104", restored.get(99).getMessage());
    }
}
