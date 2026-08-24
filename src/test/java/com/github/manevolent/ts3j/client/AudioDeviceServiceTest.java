package com.github.manevolent.ts3j.client;

import org.junit.Test;

import java.util.Arrays;

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

    @Test
    public void resamplerKeepsPcmLengthAtTeamSpeakFrameRate() {
        byte[] source = new byte[882 * 2]; // 20 ms at 44.1 kHz
        for (int i = 0; i < source.length; i += 2) {
            source[i] = 0x34;
            source[i + 1] = 0x12;
        }
        byte[] result = AudioDeviceService.resamplePcm(source, 44100.0F, 48000.0F);
        assertEquals(AudioDeviceService.VOICE_FRAME_BYTES, result.length);
        assertEquals(0x34, result[100] & 0xff);
        assertEquals(0x12, result[101] & 0xff);
    }

    @Test
    public void playbackMixerCombinesSourcesWithSaturation() {
        byte[] first = new byte[AudioDeviceService.VOICE_FRAME_BYTES];
        byte[] second = new byte[AudioDeviceService.VOICE_FRAME_BYTES];
        for (int i = 0; i < first.length; i += 2) {
            first[i] = (byte) 0x40;
            first[i + 1] = 0x1f; // 8000
            second[i] = (byte) 0x40;
            second[i + 1] = 0x1f; // 8000
        }
        byte[] mixed = AudioDeviceService.mixPcmFrames(Arrays.asList(first, second));
        assertEquals(AudioDeviceService.VOICE_FRAME_BYTES, mixed.length);
        assertEquals(16000, (short) ((mixed[0] & 0xff) | (mixed[1] << 8)));

        for (int i = 0; i < first.length; i += 2) {
            first[i] = (byte) 0xff;
            first[i + 1] = 0x7f;
            second[i] = (byte) 0xff;
            second[i + 1] = 0x7f;
        }
        mixed = AudioDeviceService.mixPcmFrames(Arrays.asList(first, second));
        assertEquals(Short.MAX_VALUE, (short) ((mixed[0] & 0xff) | (mixed[1] << 8)));
    }
}
