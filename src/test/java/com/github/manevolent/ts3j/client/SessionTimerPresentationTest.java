package com.github.manevolent.ts3j.client;

import org.junit.Test;

import java.time.Instant;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SessionTimerPresentationTest {
    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-01-01T18:00:00Z");

    @Test
    public void knownSessionKeepsNumericDuration() {
        VoiceRoomSession session = new VoiceRoomSession("server", 10, START,
                Collections.singleton(101));

        assertEquals("08:00:00", SessionTimerPresentation.value(session, NOW));
        assertTrue(SessionTimerPresentation.tooltip(session).contains(START.toString()));
    }

    @Test
    public void inheritedSessionIsShownAsActiveInsteadOfAnError() {
        VoiceRoomSession session = new VoiceRoomSession("server", 10, null, false,
                Collections.singleton(101), 1L);

        assertEquals("En curso", SessionTimerPresentation.value(session, NOW));
        assertTrue(SessionTimerPresentation.isInherited(session));
        assertTrue(SessionTimerPresentation.tooltip(session).contains("estado compartido"));
        assertTrue(SessionTimerPresentation.inheritedNotice().startsWith("Sesión activa"));
    }

    @Test
    public void noSessionStillUsesEmptyTimer() {
        assertEquals("—", SessionTimerPresentation.value(null, NOW));
        assertTrue(SessionTimerPresentation.tooltip(null).contains("No hay"));
    }
}
