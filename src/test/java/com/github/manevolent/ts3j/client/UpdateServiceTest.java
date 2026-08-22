package com.github.manevolent.ts3j.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateServiceTest {
    @Test
    public void parsesLatestReleaseTagAndWindowsAsset() throws Exception {
        String json = "{\"tag_name\":\"v1.1\",\"assets\":["
                + "{\"name\":\"ts3j-client-1.1.exe\","
                + "\"browser_download_url\":\"https://github.com/neura-neura/ts3j-client/releases/download/v1.1/ts3j-client-1.1.exe\"}]}";

        UpdateService.UpdateInfo update = UpdateService.parseLatestRelease(json);

        assertEquals("1.1", update.getVersion());
        assertEquals("ts3j-client-1.1.exe", update.getAssetName());
        assertTrue(update.getDownloadUrl().endsWith("ts3j-client-1.1.exe"));
    }

    @Test
    public void comparesReleaseVersionsWithoutTreatingTheVPrefixAsMeaningful() {
        assertEquals(0, UpdateService.compareVersions("v1.0", "1.0.0"));
        assertTrue(UpdateService.isNewer("1.0.1", "1.0"));
        assertFalse(UpdateService.isNewer("1.0", "1.0.1"));
    }
}
