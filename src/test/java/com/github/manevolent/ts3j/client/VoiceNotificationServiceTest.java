package com.github.manevolent.ts3j.client;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VoiceNotificationServiceTest {
    @Test
    public void macSpeechUsesSayArgumentsWithoutAShell() {
        Path output = Paths.get("/tmp/ts3j voice.aiff");
        String message = "Usuario; $(touch no-se-ejecuta) entró al canal";

        List<String> command = VoiceNotificationService.macSayCommand(
                message, new Locale("es", "MX"), output);

        assertEquals("/usr/bin/say", command.get(0));
        assertEquals("-v", command.get(1));
        assertEquals("Paulina", command.get(2));
        assertEquals("-o", command.get(3));
        assertEquals(output.toAbsolutePath().toString(), command.get(4));
        assertEquals(message, command.get(5));
        assertFalse(command.contains("/bin/sh"));
        assertFalse(command.contains("/bin/zsh"));
    }

    @Test
    public void macSpeechUsesLocalizedVoicesAndAppliesBoundedPlaybackVolume() {
        assertEquals("Samantha", VoiceNotificationService.preferredMacVoice(Locale.US));
        assertEquals("Paulina", VoiceNotificationService.preferredMacVoice(
                new Locale("es", "MX")));
        assertEquals("Tingting", VoiceNotificationService.preferredMacVoice(
                Locale.SIMPLIFIED_CHINESE));

        Path audio = Paths.get("/tmp/speech.aiff");
        List<String> quieter = VoiceNotificationService.macPlaybackCommand(audio, 35);
        assertEquals("/usr/bin/afplay", quieter.get(0));
        assertEquals("-v", quieter.get(1));
        assertEquals("0.35", quieter.get(2));
        assertEquals(audio.toAbsolutePath().toString(), quieter.get(3));
        assertEquals("1.0", VoiceNotificationService.macPlaybackCommand(audio, 150).get(2));
        assertEquals("0.0", VoiceNotificationService.macPlaybackCommand(audio, -1).get(2));
        assertTrue(quieter.size() == 4);
    }

    @Test
    public void messagesFollowTheSelectedLanguage() {
        TeamSpeakActivity activity = new TeamSpeakActivity(
                TeamSpeakActivity.Type.CLIENT_JOINED_CURRENT_CHANNEL,
                "neura2", "", "Default Channel", "192.168.196.65");

        assertEquals("neura2 joined your channel Default Channel",
                VoiceNotificationService.messageFor(activity, UiLanguage.ENGLISH));
        assertEquals("neura2 entró a tu canal Default Channel",
                VoiceNotificationService.messageFor(activity, UiLanguage.SPANISH));
        assertEquals("neura2加入了你的Default Channel频道",
                VoiceNotificationService.messageFor(activity, UiLanguage.CHINESE));
    }

    @Test
    public void disabledServiceDoesNotQueueAudioWork() {
        VoiceNotificationService service = new VoiceNotificationService(UiLanguage.ENGLISH, false);
        try {
            service.enqueue(new TeamSpeakActivity(TeamSpeakActivity.Type.CONNECTED,
                    "", "", "", "server"));
        } finally {
            service.close();
        }
    }

    @Test
    public void volumeIsBoundedAndCanBeChangedWhileRunning() {
        VoiceNotificationService service = new VoiceNotificationService(UiLanguage.ENGLISH, true, 35);
        try {
            assertEquals(35, service.volumePercent());
            service.setVolumePercent(150);
            assertEquals(100, service.volumePercent());
            service.setVolumePercent(-1);
            assertEquals(0, service.volumePercent());
        } finally {
            service.close();
        }
    }

    @Test
    public void windowsFallbackScalesBundledPcmWithoutChangingTheHeader() {
        byte[] wave = tinyWave((short) 10000, (short) -10000);
        byte[] quieter = VoiceNotificationService.scalePcmWave(wave, 50);

        assertEquals(5000, littleEndianShort(quieter, 44));
        assertEquals(-5000, littleEndianShort(quieter, 46));
        assertEquals('R', quieter[0]);
        assertEquals('W', quieter[8]);
    }

    private static byte[] tinyWave(short... samples) {
        byte[] result = new byte[44 + samples.length * 2];
        copy(result, 0, "RIFF".getBytes(StandardCharsets.US_ASCII));
        littleEndianInt(result, 4, result.length - 8);
        copy(result, 8, "WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        littleEndianInt(result, 16, 16);
        littleEndianShort(result, 20, (short) 1);
        littleEndianShort(result, 22, (short) 1);
        littleEndianInt(result, 24, 44100);
        littleEndianInt(result, 28, 88200);
        littleEndianShort(result, 32, (short) 2);
        littleEndianShort(result, 34, (short) 16);
        copy(result, 36, "data".getBytes(StandardCharsets.US_ASCII));
        littleEndianInt(result, 40, samples.length * 2);
        for (int index = 0; index < samples.length; index++) {
            littleEndianShort(result, 44 + index * 2, samples[index]);
        }
        return result;
    }

    private static int littleEndianShort(byte[] value, int offset) {
        return (short) ((value[offset] & 0xff) | (value[offset + 1] << 8));
    }

    private static void littleEndianShort(byte[] value, int offset, short number) {
        value[offset] = (byte) number;
        value[offset + 1] = (byte) (number >>> 8);
    }

    private static void littleEndianInt(byte[] value, int offset, int number) {
        value[offset] = (byte) number;
        value[offset + 1] = (byte) (number >>> 8);
        value[offset + 2] = (byte) (number >>> 16);
        value[offset + 3] = (byte) (number >>> 24);
    }

    private static void copy(byte[] target, int offset, byte[] source) {
        System.arraycopy(source, 0, target, offset, source.length);
    }
}
