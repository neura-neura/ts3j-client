package com.github.manevolent.ts3j.client;

import java.time.Instant;

/**
 * Human-facing copy for a shared channel timer.
 *
 * <p>A channel can be occupied when this application connects even though no
 * authoritative observer recorded the zero-to-one transition. In that case
 * the UI must communicate that the session is active without presenting a
 * fabricated numeric duration.</p>
 */
final class SessionTimerPresentation {
    private static final String ACTIVE_LABEL = "En curso";
    private static final String UNKNOWN_TOOLTIP =
            "Sesión activa antes de esta instancia. El inicio exacto aparecerá "
                    + "cuando exista en el estado compartido o lo registre un monitor autoritativo.";

    private SessionTimerPresentation() { }

    static String value(VoiceRoomSession session, Instant now) {
        return value(session, now, UiLanguage.SPANISH);
    }

    static String value(VoiceRoomSession session, Instant now, UiLanguage language) {
        if (session == null) return "—";
        if (!session.isStartKnown()) return UiText.text(language, "timer.active");
        return TimeFormatter.format(session.elapsedAt(now));
    }

    static String tooltip(VoiceRoomSession session) {
        return tooltip(session, UiLanguage.SPANISH);
    }

    static String tooltip(VoiceRoomSession session, UiLanguage language) {
        if (session == null) return UiText.text(language, "timer.none");
        if (!session.isStartKnown()) return UiText.text(language, "timer.unknown");
        return UiText.text(language, "timer.start", session.getVoiceSessionStart());
    }

    static boolean isInherited(VoiceRoomSession session) {
        return session != null && !session.isStartKnown();
    }

    static String inheritedNotice() {
        return inheritedNotice(UiLanguage.SPANISH);
    }

    static String inheritedNotice(UiLanguage language) {
        return UiText.text(language, "timer.inherited");
    }
}
