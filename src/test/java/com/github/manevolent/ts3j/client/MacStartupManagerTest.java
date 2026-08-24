package com.github.manevolent.ts3j.client;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MacStartupManagerTest {
    @Test
    public void launchAgentStartsTheInstalledLauncherInTheTray() throws Exception {
        Path root = Files.createTempDirectory("ts3j-mac-startup-test");
        Path launchAgents = root.resolve("Library").resolve("LaunchAgents");
        Path launcher = root.resolve("ts3j-client launcher");
        Files.write(launcher, new byte[] {1});
        MacStartupManager manager = new MacStartupManager(launchAgents);
        try {
            manager.setEnabled(true, launcher);

            assertTrue(manager.isEnabled());
            String plist = new String(Files.readAllBytes(manager.launchAgent()),
                    StandardCharsets.UTF_8);
            assertTrue(plist.contains("<key>ProgramArguments</key>"));
            assertTrue(plist.contains("<string>" + launcher.toAbsolutePath() + "</string>"));
            assertTrue(plist.contains("<string>--tray</string>"));
            assertTrue(plist.contains("<key>RunAtLoad</key>"));

            manager.setEnabled(false, null);
            assertFalse(manager.isEnabled());
        } finally {
            Files.deleteIfExists(manager.launchAgent());
            Files.deleteIfExists(launcher);
            Files.deleteIfExists(launchAgents);
            Files.deleteIfExists(launchAgents.getParent());
            Files.deleteIfExists(root);
        }
    }

    @Test
    public void plistEscapesEveryXmlSensitiveLauncherCharacter() {
        Path launcher = Paths.get("/Applications/TS3J & <Client> \"beta\" 'one'/launcher");

        String plist = MacStartupManager.plistFor(launcher);

        assertTrue(plist.contains("TS3J &amp; &lt;Client&gt; &quot;beta&quot; &apos;one&apos;"));
        assertFalse(plist.contains("TS3J & <Client>"));
    }

    @Test
    public void defaultPathUsesTheCurrentUsersLaunchAgentsDirectory() {
        assertEquals(Paths.get("/Users/tester", "Library", "LaunchAgents"),
                MacStartupManager.defaultLaunchAgentsDirectory("/Users/tester"));
        assertTrue(MacStartupManager.isMacOs("Mac OS X"));
        assertFalse(MacStartupManager.isMacOs("Windows 11"));
        assertFalse(MacStartupManager.isSuitableLauncher(
                Paths.get("/Volumes/ts3j-client/ts3j-client.app/Contents/MacOS/ts3j-client")));
    }
}
