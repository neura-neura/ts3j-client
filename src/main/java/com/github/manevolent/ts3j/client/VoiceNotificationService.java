package com.github.manevolent.ts3j.client;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Serializes TeamSpeak activity announcements so overlapping events do not
 * talk over one another. Windows SAPI provides the spoken message, while the
 * bundled app-owned cue pack remains available when SAPI cannot start.
 */
final class VoiceNotificationService implements AutoCloseable {
    private static final String SOUND_RESOURCE_ROOT =
            "/com/github/manevolent/ts3j/client/sounds/";
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ts3j-client-voice-notifications");
        thread.setDaemon(true);
        return thread;
    });
    /**
     * Outbound chat cues are local feedback and must not wait behind a long
     * SAPI announcement or a join/leave notification. A dedicated serial lane
     * still prevents consecutive clicks from starting overlapping cues.
     */
    private final ExecutorService immediateExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ts3j-client-chat-notifications");
        thread.setDaemon(true);
        return thread;
    });
    private volatile UiLanguage language;
    private volatile boolean enabled;
    private volatile int volumePercent;
    private volatile boolean closed;

    VoiceNotificationService(UiLanguage language, boolean enabled) {
        this(language, enabled, 100);
    }

    VoiceNotificationService(UiLanguage language, boolean enabled, int volumePercent) {
        this.language = language == null ? UiLanguage.ENGLISH : language;
        this.enabled = enabled;
        this.volumePercent = boundedVolume(volumePercent);
    }

    void setLanguage(UiLanguage value) {
        language = value == null ? UiLanguage.ENGLISH : value;
    }

    void setEnabled(boolean value) {
        enabled = value;
    }

    void setVolumePercent(int value) {
        volumePercent = boundedVolume(value);
    }

    int volumePercent() {
        return volumePercent;
    }

    boolean isEnabled() {
        return enabled;
    }

    void enqueue(TeamSpeakActivity activity) {
        if (activity == null || closed || !enabled) return;
        try {
            ExecutorService destination = activity.getType() == TeamSpeakActivity.Type.CHAT_SENT
                    ? immediateExecutor : executor;
            destination.execute(() -> announce(activity));
        } catch (RuntimeException ignored) {
            // Shutdown races must never leak into the TeamSpeak event thread.
        }
    }

    private void announce(TeamSpeakActivity activity) {
        if (closed || !enabled || volumePercent <= 0) return;
        UiLanguage currentLanguage = language;
        int currentVolume = volumePercent;
        String message = messageFor(activity, currentLanguage);
        String soundFile = soundFileFor(activity.getType());
        if (soundFile != null && !prefersSpeech(activity.getType())
                && playSound(soundFile, currentVolume)) return;
        if (!message.isEmpty() && speak(message, currentLanguage.getLocale(), currentVolume)) return;
        if (soundFile != null) playSound(soundFile, currentVolume);
    }

    static String messageFor(TeamSpeakActivity activity, UiLanguage language) {
        if (activity == null) return "";
        UiLanguage current = language == null ? UiLanguage.ENGLISH : language;
        String client = valueOr(activity.getClientName(), current == UiLanguage.CHINESE ? "用户" : "user");
        String channel = valueOr(activity.getChannelName(), current == UiLanguage.CHINESE ? "频道" : "channel");
        String from = valueOr(activity.getFromChannelName(), current == UiLanguage.CHINESE ? "频道" : "channel");
        String server = valueOr(activity.getServerName(), current == UiLanguage.CHINESE ? "服务器" : "server");
        switch (activity.getType()) {
            case CONNECTED:
                return current == UiLanguage.SPANISH ? "Conectado a " + server
                        : current == UiLanguage.CHINESE ? "已连接到" + server : "Connected to " + server;
            case DISCONNECTED:
                return current == UiLanguage.SPANISH ? "Desconectado de " + server
                        : current == UiLanguage.CHINESE ? "已断开与" + server + "的连接" : "Disconnected from " + server;
            case YOU_SWITCHED_CHANNEL:
                return current == UiLanguage.SPANISH ? "Cambiaste al canal " + channel
                        : current == UiLanguage.CHINESE ? "你已切换到" + channel + "频道" : "You switched to channel " + channel;
            case CLIENT_JOINED_CURRENT_CHANNEL:
            case CLIENT_SWITCHED_TO_CURRENT_CHANNEL:
                return current == UiLanguage.SPANISH ? client + " entró a tu canal " + channel
                        : current == UiLanguage.CHINESE ? client + "加入了你的" + channel + "频道" : client + " joined your channel " + channel;
            case CLIENT_LEFT_CURRENT_CHANNEL:
            case CLIENT_SWITCHED_FROM_CURRENT_CHANNEL:
                return current == UiLanguage.SPANISH ? client + " salió de tu canal " + from
                        : current == UiLanguage.CHINESE ? client + "离开了你的" + from + "频道" : client + " left your channel " + from;
            case MICROPHONE_MUTED:
                return current == UiLanguage.SPANISH ? "Micrófono silenciado"
                        : current == UiLanguage.CHINESE ? "麦克风已静音" : "Microphone muted";
            case MICROPHONE_UNMUTED:
                return current == UiLanguage.SPANISH ? "Micrófono activado"
                        : current == UiLanguage.CHINESE ? "麦克风已启用" : "Microphone activated";
            case AUDIO_MUTED:
                return current == UiLanguage.SPANISH ? "Audio silenciado"
                        : current == UiLanguage.CHINESE ? "音频已静音" : "Sound muted";
            case AUDIO_UNMUTED:
                return current == UiLanguage.SPANISH ? "Audio activado"
                        : current == UiLanguage.CHINESE ? "音频已恢复" : "Sound resumed";
            case AWAY_ENABLED:
                return current == UiLanguage.SPANISH ? "Estado ausente activado"
                        : current == UiLanguage.CHINESE ? "已设置为离开状态" : "Away status enabled";
            case AWAY_DISABLED:
                return current == UiLanguage.SPANISH ? "Estado disponible activado"
                        : current == UiLanguage.CHINESE ? "已恢复在线状态" : "Away status disabled";
            case CHAT_RECEIVED:
                return current == UiLanguage.SPANISH ? "Mensaje recibido de " + client
                        : current == UiLanguage.CHINESE ? "收到来自" + client + "的消息" : "Message received from " + client;
            case CHAT_SENT:
                return current == UiLanguage.SPANISH ? "Mensaje enviado"
                        : current == UiLanguage.CHINESE ? "消息已发送" : "Message sent";
            case POKE:
                return current == UiLanguage.SPANISH ? client + " te dio un toque"
                        : current == UiLanguage.CHINESE ? client + "戳了你一下" : client + " poked you";
            default:
                return "";
        }
    }

    private boolean playSound(String fileName, int volume) {
        URL resource = getClass().getResource(SOUND_RESOURCE_ROOT + fileName);
        if (resource == null) return false;
        if (isWindows() && playSoundWithWindowsMixer(resource, volume)) return true;
        return playSoundWithJavaSound(resource, volume);
    }

    private boolean playSoundWithJavaSound(URL resource, int volume) {
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(resource)) {
            Clip clip = AudioSystem.getClip();
            try {
                clip.open(stream);
                applyVolume(clip, volume);
                clip.start();
                while (clip.isRunning() && !closed && enabled) {
                    Thread.sleep(25L);
                }
            } finally {
                clip.close();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * JavaSound mixers are not consistently exposed by the custom runtime on
     * Windows. SoundPlayer is a small, built-in Windows fallback that still
     * consumes the WAV shipped in this application; it does not inspect or
     * depend on the official TeamSpeak installation.
     */
    private boolean playSoundWithWindowsMixer(URL resource, int volume) {
        Path temporaryWave = null;
        try {
            byte[] source;
            try (InputStream input = resource.openStream()) {
                source = input.readAllBytes();
            }
            temporaryWave = Files.createTempFile("ts3j-client-cue-", ".wav");
            Files.write(temporaryWave, scalePcmWave(source, volume),
                    StandardOpenOption.TRUNCATE_EXISTING);
            String script = "$ErrorActionPreference='Stop';"
                    + "Add-Type -AssemblyName System;"
                    + "$p=New-Object System.Media.SoundPlayer('"
                    + escapeSingleQuoted(temporaryWave.toString()) + "');"
                    + "$p.Load();$p.PlaySync();$p.Dispose();";
            return runPowerShell(script, 15L);
        } catch (Exception ignored) {
            return false;
        } finally {
            if (temporaryWave != null) {
                try {
                    Files.deleteIfExists(temporaryWave);
                } catch (Exception ignored) {
                    // The OS can remove the short-lived file after playback.
                }
            }
        }
    }

    /** Scales the 16-bit PCM data chunk without changing the packaged WAV format. */
    static byte[] scalePcmWave(byte[] source, int volume) {
        if (source == null || source.length < 12 || boundedVolume(volume) >= 100) {
            return source == null ? new byte[0] : source;
        }
        byte[] result = Arrays.copyOf(source, source.length);
        if (result[0] != 'R' || result[1] != 'I' || result[2] != 'F' || result[3] != 'F'
                || result[8] != 'W' || result[9] != 'A' || result[10] != 'V' || result[11] != 'E') {
            return result;
        }
        int dataStart = -1;
        int dataEnd = -1;
        int offset = 12;
        while (offset + 8 <= result.length) {
            int chunkSize = littleEndianInt(result, offset + 4);
            if (chunkSize < 0 || offset + 8L + chunkSize > result.length) break;
            if (result[offset] == 'd' && result[offset + 1] == 'a'
                    && result[offset + 2] == 't' && result[offset + 3] == 'a') {
                dataStart = offset + 8;
                dataEnd = dataStart + chunkSize;
                break;
            }
            offset += 8 + chunkSize + (chunkSize & 1);
        }
        if (dataStart < 0 || dataEnd > result.length) return result;
        double multiplier = boundedVolume(volume) / 100.0D;
        for (int index = dataStart; index + 1 < dataEnd; index += 2) {
            int sample = (short) ((result[index] & 0xff) | (result[index + 1] << 8));
            int scaled = (int) Math.round(sample * multiplier);
            scaled = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, scaled));
            result[index] = (byte) (scaled & 0xff);
            result[index + 1] = (byte) ((scaled >>> 8) & 0xff);
        }
        return result;
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static void applyVolume(Clip clip, int volume) {
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            double normalized = boundedVolume(volume) / 100.0D;
            float decibels = normalized <= 0.0D
                    ? gain.getMinimum()
                    : (float) (20.0D * Math.log10(normalized));
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
        } catch (IllegalArgumentException ignored) {
            // Some audio mixers do not expose a master-gain control.
        }
    }

    private boolean speak(String message, Locale locale, int volume) {
        if (!isWindows() || message == null || message.trim().isEmpty()) return false;
        String languageTag = locale == null ? "en" : locale.getLanguage();
        String script = "$ErrorActionPreference='Stop';"
                + "Add-Type -AssemblyName System.Speech;"
                + "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer;"
                + "try{$v=$s.GetInstalledVoices()|Where-Object {$_.VoiceInfo.Culture.Name -like '"
                + escapeSingleQuoted(languageTag) + "*'};"
                + "if($v){$s.SelectVoice($v[0].VoiceInfo.Name)};"
                + "$s.Volume=" + boundedVolume(volume) + ";"
                + "$s.Speak('" + escapeSingleQuoted(message) + "')}finally{$s.Dispose()}";
        return runPowerShell(script, 15L);
    }

    private boolean runPowerShell(String script, long timeoutSeconds) {
        if (!isWindows() || script == null || script.isEmpty()) return false;
        String powershell = powershellExecutable();
        if (powershell == null) return false;
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        Process process = null;
        try {
            process = new ProcessBuilder(powershell, "-NoLogo", "-NoProfile", "-NonInteractive",
                    "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
                    .redirectErrorStream(true).start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static String soundFileFor(TeamSpeakActivity.Type type) {
        switch (type) {
            case CONNECTED: return "connected.wav";
            case DISCONNECTED: return "disconnected.wav";
            case YOU_SWITCHED_CHANNEL: return "channel_switched.wav";
            case CLIENT_JOINED_CURRENT_CHANNEL:
            case CLIENT_SWITCHED_TO_CURRENT_CHANNEL: return "user_joined.wav";
            case CLIENT_LEFT_CURRENT_CHANNEL:
            case CLIENT_SWITCHED_FROM_CURRENT_CHANNEL: return "user_left.wav";
            case MICROPHONE_MUTED: return "mic_muted.wav";
            case MICROPHONE_UNMUTED: return "mic_activated.wav";
            case AUDIO_MUTED: return "sound_muted.wav";
            case AUDIO_UNMUTED: return "sound_resumed.wav";
            case AWAY_ENABLED: return "away_activated.wav";
            case AWAY_DISABLED: return "away_deactivated.wav";
            case CHAT_RECEIVED: return "chat_message_inbound.wav";
            case CHAT_SENT: return "chat_message_outbound.wav";
            case POKE: return "you_were_poked.wav";
            default: return null;
        }
    }

    private static boolean prefersSpeech(TeamSpeakActivity.Type type) {
        switch (type) {
            case YOU_SWITCHED_CHANNEL:
            case CLIENT_JOINED_CURRENT_CHANNEL:
            case CLIENT_LEFT_CURRENT_CHANNEL:
            case CLIENT_SWITCHED_TO_CURRENT_CHANNEL:
            case CLIENT_SWITCHED_FROM_CURRENT_CHANNEL:
                return true;
            default:
                return false;
        }
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String escapeSingleQuoted(String value) {
        return value == null ? "" : value.replace("'", "''").replace("\u0000", "");
    }

    private static String powershellExecutable() {
        String windows = System.getenv("WINDIR");
        if (windows != null && !windows.trim().isEmpty()) {
            Path candidate = Paths.get(windows, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            if (Files.isRegularFile(candidate)) return candidate.toString();
        }
        return isWindows() ? "powershell.exe" : null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static int boundedVolume(int value) {
        return Math.max(0, Math.min(100, value));
    }

    @Override
    public void close() {
        closed = true;
        executor.shutdownNow();
        immediateExecutor.shutdownNow();
        try {
            executor.awaitTermination(2L, TimeUnit.SECONDS);
            immediateExecutor.awaitTermination(2L, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
