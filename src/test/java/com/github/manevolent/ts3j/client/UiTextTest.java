package com.github.manevolent.ts3j.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UiTextTest {
    @Test
    public void englishIsTheDefaultAndAllSupportedLanguagesHaveLabels() {
        assertEquals("Preferences", UiText.text(UiLanguage.ENGLISH, "settings.title"));
        assertEquals("Preferencias", UiText.text(UiLanguage.SPANISH, "settings.title"));
        assertEquals("偏好设置", UiText.text(UiLanguage.CHINESE, "settings.title"));
        assertEquals("English", UiLanguage.fromCode(null).toString());
    }

    @Test
    public void formattedTextUsesTheSelectedLanguage() {
        assertEquals("PEOPLE · 2", UiText.text(UiLanguage.ENGLISH, "people", 2));
        assertEquals("PERSONAS · 2", UiText.text(UiLanguage.SPANISH, "people", 2));
        assertEquals("用户 · 2", UiText.text(UiLanguage.CHINESE, "people", 2));
    }
}
