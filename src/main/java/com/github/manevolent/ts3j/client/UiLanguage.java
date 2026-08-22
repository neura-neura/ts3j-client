package com.github.manevolent.ts3j.client;

import java.util.Locale;

/** Supported interface languages persisted in the desktop preferences. */
enum UiLanguage {
    ENGLISH("en", "English", Locale.ENGLISH),
    SPANISH("es", "Español", new Locale("es", "MX")),
    CHINESE("zh", "中文", Locale.SIMPLIFIED_CHINESE);

    private final String code;
    private final String displayName;
    private final Locale locale;

    UiLanguage(String code, String displayName, Locale locale) {
        this.code = code;
        this.displayName = displayName;
        this.locale = locale;
    }

    String getCode() {
        return code;
    }

    Locale getLocale() {
        return locale;
    }

    @Override
    public String toString() {
        return displayName;
    }

    static UiLanguage fromCode(String code) {
        if (code != null) {
            for (UiLanguage value : values()) {
                if (value.code.equalsIgnoreCase(code.trim())) return value;
            }
        }
        return ENGLISH;
    }
}
