package com.github.manevolent.ts3j.client;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GatewaySnapshotTest {
    @Test
    public void sessionStateReadinessPreventsRenderingDuringInitialSync() {
        GatewaySnapshot pending = new GatewaySnapshot(ConnectionStatus.CONNECTING, "server", "",
                -1, -1, Collections.<Integer, ChannelView>emptyMap(),
                Collections.<Integer, ClientView>emptyMap(),
                new SessionSnapshot(Collections.<SessionKey, VoiceRoomSession>emptyMap(), 4L), false);

        assertFalse(pending.isSessionStateReady());
    }

    @Test
    public void legacySnapshotsRemainReadyByDefault() {
        GatewaySnapshot ready = new GatewaySnapshot(ConnectionStatus.CONNECTED_NO_CHANNEL, "server", "",
                -1, -1, Collections.<Integer, ChannelView>emptyMap(),
                Collections.<Integer, ClientView>emptyMap(),
                new SessionSnapshot(Collections.<SessionKey, VoiceRoomSession>emptyMap(), 4L));

        assertTrue(ready.isSessionStateReady());
    }

    @Test
    public void historyBoundariesAreCopiedIntoImmutableSnapshots() {
        Map<Integer, Integer> boundaries = new LinkedHashMap<>();
        boundaries.put(7, 3);
        GatewaySnapshot snapshot = new GatewaySnapshot(ConnectionStatus.CONNECTED_NO_CHANNEL, "server", "",
                -1, -1, Collections.<Integer, ChannelView>emptyMap(),
                Collections.<Integer, ClientView>emptyMap(),
                new SessionSnapshot(Collections.<SessionKey, VoiceRoomSession>emptyMap(), 4L),
                Collections.<Integer, java.util.List<ChannelTextMessage>>emptyMap(), boundaries, true);
        boundaries.put(7, 99);

        assertEquals(Integer.valueOf(3), snapshot.getChannelHistoryBoundaries().get(7));
        try {
            snapshot.getChannelHistoryBoundaries().put(8, 1);
            throw new AssertionError("Expected immutable boundaries");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    public void restrictedPermissionSnapshotsRemainConnectedAndExposeTheNoticeState() {
        GatewaySnapshot snapshot = new GatewaySnapshot(ConnectionStatus.CONNECTED_IN_CHANNEL, "server", "",
                7, 1, Collections.<Integer, ChannelView>emptyMap(),
                Collections.<Integer, ClientView>emptyMap(),
                new SessionSnapshot(Collections.<SessionKey, VoiceRoomSession>emptyMap(), 4L),
                Collections.<Integer, java.util.List<ChannelTextMessage>>emptyMap(),
                Collections.<Integer, Integer>emptyMap(), true, true);

        assertEquals(ConnectionStatus.CONNECTED_IN_CHANNEL, snapshot.getStatus());
        assertTrue(snapshot.isPermissionsLimited());
    }
}
