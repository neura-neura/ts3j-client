package com.github.manevolent.ts3j.client;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DesktopPreferencesTest {
    @Test
    public void appPreferencesUseTrayAsTheDefaultCloseBehavior() throws Exception {
        Preferences preferences = Preferences.userRoot().node("ts3j-client-test/desktop-" + UUID.randomUUID());
        try {
            AppPreferences store = new AppPreferences(preferences);
            assertFalse(store.startsWithWindows());
            assertTrue(store.closesToTray());
            assertFalse(store.isLightTheme());
            assertEquals(UiLanguage.ENGLISH, store.language());
            assertTrue(store.voiceNotifications());
            assertEquals(100, store.voiceNotificationVolume());
            store.setStartsWithWindows(true);
            store.setClosesToTray(false);
            store.setLightTheme(true);
            store.setLanguage(UiLanguage.CHINESE);
            store.setVoiceNotifications(false);
            store.setVoiceNotificationVolume(37);
            assertTrue(store.startsWithWindows());
            assertFalse(store.closesToTray());
            assertTrue(store.isLightTheme());
            assertEquals(UiLanguage.CHINESE, store.language());
            assertFalse(store.voiceNotifications());
            assertEquals(37, store.voiceNotificationVolume());
            AppPreferences reloaded = new AppPreferences(preferences);
            assertEquals(37, reloaded.voiceNotificationVolume());
            store.setVoiceNotificationVolume(150);
            assertEquals(100, store.voiceNotificationVolume());
            store.setVoiceNotificationVolume(-10);
            assertEquals(0, store.voiceNotificationVolume());
        } finally {
            preferences.removeNode();
        }
    }

    @Test
    public void clientVolumeIsBoundedAndZeroRemovesTheOverride() throws Exception {
        Preferences preferences = Preferences.userRoot().node("ts3j-client-test/volume-" + UUID.randomUUID());
        try {
            ClientVolumeStore store = new ClientVolumeStore(preferences);
            ClientView client = new ClientView(7, 42, "neura", "uid-neura", 0, false, false, false);
            assertEquals(0.0D, store.get("192.168.196.65:9987", client), 0.001D);
            store.set("192.168.196.65:9987", client, 99.0D);
            assertEquals(20.0D, store.get("192.168.196.65:9987", client), 0.001D);
            store.set("192.168.196.65:9987", client, -99.0D);
            assertEquals(-50.0D, store.get("192.168.196.65:9987", client), 0.001D);
            store.set("192.168.196.65:9987", client, 0.0D);
            assertFalse(store.isModified("192.168.196.65:9987", client));
        } finally {
            preferences.removeNode();
        }
    }

    @Test
    public void startupScriptLaunchesTheInstalledAppInTray() throws Exception {
        Path startup = Files.createTempDirectory("ts3j-startup-test");
        Path executable = startup.resolve("TeamSpeak Client\\ts3j-client.exe");
        Files.createDirectories(executable.getParent());
        Files.write(executable, new byte[] {1});
        WindowsStartupManager manager = new WindowsStartupManager(startup);
        try {
            manager.setEnabled(true, executable);
            assertTrue(manager.isEnabled());
            String script = new String(Files.readAllBytes(manager.startupScript()), "UTF-8");
            assertTrue(script.contains("ts3j-client.exe"));
            assertTrue(script.contains("--tray"));
            manager.setEnabled(false, executable);
            assertFalse(manager.isEnabled());
        } finally {
            Files.deleteIfExists(manager.startupScript());
            Files.deleteIfExists(executable);
            Files.deleteIfExists(executable.getParent());
            Files.deleteIfExists(startup);
        }
    }
}
