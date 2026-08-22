package com.github.manevolent.ts3j.client;

import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.Assert.assertEquals;

public class ConnectionProfileStoreTest {
    @Test
    public void roundTripRestoresAllConnectionFields() throws Exception {
        Preferences preferences = Preferences.userRoot().node(
                "ts3j-client-test/" + UUID.randomUUID());
        try {
            ConnectionProfileStore store = new ConnectionProfileStore(preferences);
            Path state = Paths.get("C:/shared/voice-sessions.db");
            store.save(" 192.168.196.65 ", " 9987 ", " neura ", "test-secret", state);

            ConnectionProfileStore.ConnectionProfile restored = store.load(Paths.get("fallback.db"));
            assertEquals("192.168.196.65", restored.getHost());
            assertEquals("9987", restored.getPort());
            assertEquals("neura", restored.getNickname());
            assertEquals("test-secret", restored.getPassword());
            assertEquals(state, restored.getStatePath());
        } finally {
            preferences.removeNode();
        }
    }

    @Test
    public void missingValuesUseSafeDefaults() throws Exception {
        Preferences preferences = Preferences.userRoot().node(
                "ts3j-client-test/" + UUID.randomUUID());
        try {
            Path fallback = Paths.get("fallback.db");
            ConnectionProfileStore.ConnectionProfile restored =
                    new ConnectionProfileStore(preferences).load(fallback);
            assertEquals("localhost", restored.getHost());
            assertEquals("", restored.getPort());
            assertEquals("ts3j-client", restored.getNickname());
            assertEquals("", restored.getPassword());
            assertEquals(fallback, restored.getStatePath());
        } finally {
            preferences.removeNode();
        }
    }
}
