package com.github.manevolent.ts3j.client;

import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class ConnectionConfigTest {
    @Test
    public void blankPortUsesTeamSpeakDefault() {
        ConnectionConfig config = new ConnectionConfig("192.168.196.65", "", "", "neura",
                Paths.get("state.db"));

        assertEquals(ConnectionConfig.DEFAULT_VOICE_PORT, config.getPort());
        assertEquals("192.168.196.65:9987", config.serverId());
    }

    @Test
    public void explicitPortIsPreserved() {
        assertEquals(10011, ConnectionConfig.parsePort(" 10011 "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidPortIsRejected() {
        ConnectionConfig.parsePort("not-a-port");
    }
}
