package com.github.manevolent.ts3j.client;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UpdateServiceTest {
    @Test
    public void parsesLatestReleaseTagAndWindowsAsset() throws Exception {
        String json = "{\"tag_name\":\"v1.1\",\"assets\":["
                + "{\"name\":\"ts3j-client-1.1.dmg\","
                + "\"browser_download_url\":\"https://github.com/neura-neura/ts3j-client/releases/download/v1.1/ts3j-client-1.1.dmg\"},"
                + "{\"name\":\"ts3j-client-1.1.exe\","
                + "\"browser_download_url\":\"https://github.com/neura-neura/ts3j-client/releases/download/v1.1/ts3j-client-1.1.exe\"}]}";

        UpdateService.UpdateInfo update = UpdateService.parseLatestRelease(json, "Windows 11");

        assertEquals("1.1", update.getVersion());
        assertEquals("ts3j-client-1.1.exe", update.getAssetName());
        assertTrue(update.getDownloadUrl().endsWith("ts3j-client-1.1.exe"));
        assertEquals(".exe", UpdateService.installerSuffix(update));
    }

    @Test
    public void macPrefersDmgOverPkgRegardlessOfAssetOrder() throws Exception {
        String json = "{\"tag_name\":\"v1.2\",\"assets\":["
                + "{\"browser_download_url\":\"https://github.com/neura-neura/ts3j-client/releases/download/v1.2/ts3j-client-1.2.pkg\"},"
                + "{\"browser_download_url\":\"https://github.com/neura-neura/ts3j-client/releases/download/v1.2/ts3j-client-1.2.exe\"},"
                + "{\"browser_download_url\":\"https://github.com/neura-neura/ts3j-client/releases/download/v1.2/ts3j-client-1.2.dmg\"}]}";

        UpdateService.UpdateInfo update = UpdateService.parseLatestRelease(json, "Mac OS X");

        assertEquals("ts3j-client-1.2.dmg", update.getAssetName());
        assertEquals(".dmg", UpdateService.installerSuffix(update));
    }

    @Test
    public void macFallsBackToPkgWhenReleaseHasNoDmg() throws Exception {
        String json = "{\"tag_name\":\"1.2\",\"assets\":["
                + "{\"browser_download_url\":\"https://github.com/neura-neura/ts3j-client/releases/download/v1.2/ts3j-client-1.2.exe\"},"
                + "{\"browser_download_url\":\"https://github.com/neura-neura/ts3j-client/releases/download/v1.2/ts3j-client-1.2.pkg\"}]}";

        UpdateService.UpdateInfo update = UpdateService.parseLatestRelease(json, "Darwin");

        assertEquals("ts3j-client-1.2.pkg", update.getAssetName());
        assertEquals(".pkg", UpdateService.installerSuffix(update));
    }

    @Test
    public void releaseVersionIsAvailableWhenItOnlyHasAnotherPlatformsInstaller() throws Exception {
        String json = "{\"tag_name\":\"v1.0.5\",\"assets\":["
                + "{\"browser_download_url\":\"https://github.com/neura-neura/ts3j-client/"
                + "releases/download/v1.0.5/ts3j-client-1.0.5.exe\"}]}";

        UpdateService.UpdateInfo update = UpdateService.parseLatestRelease(json, "Mac OS X");

        assertEquals("1.0.5", update.getVersion());
        assertEquals("", update.getAssetName());
        assertNull(update.getDownloadUrl());
    }

    @Test
    public void buildsPlatformSpecificInstallerLaunchCommands() {
        Path installer = Paths.get(System.getProperty("java.io.tmpdir"), "ts3j client update.dmg")
                .toAbsolutePath();

        assertEquals(Collections.singletonList(installer.toString()),
                UpdateService.launchCommand(installer, "Windows 10"));
        assertEquals(Arrays.asList("/usr/bin/open", installer.toString()),
                UpdateService.launchCommand(installer, "Mac OS X"));
    }

    @Test
    public void buildsPlatformSpecificInstallerVerificationCommands() {
        Path dmg = Paths.get(System.getProperty("java.io.tmpdir"), "ts3j update.dmg")
                .toAbsolutePath();
        Path pkg = Paths.get(System.getProperty("java.io.tmpdir"), "ts3j update.pkg")
                .toAbsolutePath();

        assertEquals(Collections.emptyList(),
                UpdateService.verificationCommand(dmg, "Windows 11"));
        assertEquals(Arrays.asList("/usr/bin/hdiutil", "verify", dmg.toString()),
                UpdateService.verificationCommand(dmg, "Mac OS X"));
        assertEquals(Arrays.asList("/usr/sbin/pkgutil", "--check-signature", pkg.toString()),
                UpdateService.verificationCommand(pkg, "Darwin"));
    }

    @Test
    public void rejectsMacAssetsThatAreNotTrustedGithubHttpsUrls() throws Exception {
        assertInvalidMacAsset("http://github.com/neura-neura/ts3j-client/update.dmg");
        assertInvalidMacAsset("https://example.com/neura-neura/ts3j-client/update.dmg");
    }

    @Test
    public void comparesReleaseVersionsWithoutTreatingTheVPrefixAsMeaningful() {
        assertEquals(0, UpdateService.compareVersions("v1.0", "1.0.0"));
        assertTrue(UpdateService.isNewer("1.0.1", "1.0"));
        assertFalse(UpdateService.isNewer("1.0", "1.0.1"));
    }

    private static void assertInvalidMacAsset(String downloadUrl) throws Exception {
        String json = "{\"tag_name\":\"1.2\",\"assets\":[{\"browser_download_url\":\""
                + downloadUrl + "\"}]}";
        try {
            UpdateService.parseLatestRelease(json, "Mac OS X");
            fail("Expected an untrusted installer URL to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("invalid installer URL"));
        }
    }
}
