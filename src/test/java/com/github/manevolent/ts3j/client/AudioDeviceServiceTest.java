package com.github.manevolent.ts3j.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AudioDeviceServiceTest {
    @Test
    public void rmsMeterTreatsSilenceAsZeroAndLoudPcmAsActive() {
        assertEquals(0.0D, AudioDeviceService.computeRms(new byte[128], 128), 0.0001D);
        byte[] loud = new byte[128];
        for (int i = 0; i < loud.length; i += 2) {
            loud[i] = (byte) 0xff;
            loud[i + 1] = 0x7f;
        }
        assertTrue(AudioDeviceService.computeRms(loud, loud.length) > 0.9D);
    }

    @Test
    public void meterHandlesOddAndOversizedBuffersWithoutThrowing() {
        assertTrue(AudioDeviceService.computeRms(new byte[] {0, 1, 0}, 99) >= 0.0D);
        assertEquals(0.0D, AudioDeviceService.computeRms(null, 4), 0.0001D);
    }

    @Test
    public void voiceDetectorTurnsOnImmediatelyAndReleasesAfterAQuietHold() {
        VoiceActivityDetector detector = new VoiceActivityDetector();
        assertFalse(detector.update(0.0D, 0L));
        assertTrue(detector.update(0.05D, 1L));
        assertTrue(detector.update(0.0D, 100_000_000L));
        assertFalse(detector.update(0.0D, 100_000_000L + VoiceActivityDetector.RELEASE_NANOS + 1L));
    }

    @Test
    public void shortQuietGapDoesNotTurnTheIndicatorOff() {
        VoiceActivityDetector detector = new VoiceActivityDetector();
        assertTrue(detector.update(0.05D, 0L));
        assertTrue(detector.update(0.0D, 10L));
        assertTrue(detector.update(0.03D, 20L));
        assertTrue(detector.isActive());
    }
}
