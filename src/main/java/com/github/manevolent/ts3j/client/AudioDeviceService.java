package com.github.manevolent.ts3j.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Small Java Sound monitor for real input/output devices. It is deliberately
 * independent from TeamSpeak's transport because ts3j does not provide a
 * local codec or playback mixer implementation.
 */
final class AudioDeviceService implements AutoCloseable {
    private static final float[] SAMPLE_RATES = {44100.0F, 48000.0F, 16000.0F};
    private static final AudioFormat OUTPUT_FORMAT = pcmFormat(44100.0F);
    /** RMS level at which the tray indicator turns on immediately. */
    static final double VOICE_ON_THRESHOLD = VoiceActivityDetector.ON_THRESHOLD;

    private final Object captureLock = new Object();
    private volatile String captureDeviceId;
    private volatile String playbackDeviceId;
    private volatile boolean running;
    private volatile TargetDataLine captureLine;
    private volatile SourceDataLine playbackLine;
    private volatile Thread captureThread;
    private volatile Thread playbackThread;
    private volatile double captureLevel;
    private volatile double outputLevel;
    private final VoiceActivityDetector voiceActivityDetector = new VoiceActivityDetector();
    private volatile Consumer<Double> captureLevelListener = level -> { };
    private volatile Consumer<Double> outputLevelListener = level -> { };

    AudioDeviceService(String captureDeviceId, String playbackDeviceId) {
        this.captureDeviceId = normalize(captureDeviceId);
        this.playbackDeviceId = normalize(playbackDeviceId);
    }

    void setCaptureLevelListener(Consumer<Double> listener) {
        captureLevelListener = listener == null ? level -> { } : listener;
    }

    void setOutputLevelListener(Consumer<Double> listener) {
        outputLevelListener = listener == null ? level -> { } : listener;
    }

    String getCaptureDeviceId() { return captureDeviceId; }
    String getPlaybackDeviceId() { return playbackDeviceId; }
    double getCaptureLevel() { return captureLevel; }
    double getOutputLevel() { return outputLevel; }

    boolean isVoiceDetected() {
        return voiceActivityDetector.isActive();
    }

    void start() {
        if (running) return;
        running = true;
        restartCapture();
    }

    void setCaptureDeviceId(String id) {
        captureDeviceId = normalize(id);
        if (running) restartCapture();
    }

    void setPlaybackDeviceId(String id) {
        playbackDeviceId = normalize(id);
    }

    void playTestTone() {
        playTestTone(playbackDeviceId);
    }

    void playTestTone(String deviceId) {
        if (!running) return;
        Thread previous = playbackThread;
        if (previous != null) previous.interrupt();
        String selected = normalize(deviceId);
        Thread next = new Thread(() -> playTone(selected), "ts3j-client-audio-test");
        next.setDaemon(true);
        playbackThread = next;
        next.start();
    }

    private void restartCapture() {
        synchronized (captureLock) {
            closeCaptureLine();
            if (!running) return;
            final TargetDataLine line = openCaptureLine(captureDeviceId);
            if (line == null) {
                publishCapture(0.0D);
                return;
            }
            captureLine = line;
            Thread next = new Thread(() -> captureLoop(line), "ts3j-client-microphone-meter");
            next.setDaemon(true);
            captureThread = next;
            next.start();
        }
    }

    private void captureLoop(TargetDataLine line) {
        // Keep the attack responsive for short words while the state
        // hysteresis below prevents the extra samples from flashing the tray.
        byte[] buffer = new byte[2048];
        try {
            while (running && captureLine == line && !Thread.currentThread().isInterrupted()) {
                int read = line.read(buffer, 0, buffer.length);
                if (read > 0) publishCapture(computeRms(buffer, read));
            }
        } catch (RuntimeException ignored) {
            // A device can disappear while the user changes Windows audio settings.
        } finally {
            if (captureLine == line) {
                captureLine = null;
                try { line.stop(); } catch (Exception ignored) { }
                try { line.close(); } catch (Exception ignored) { }
            }
            publishCapture(0.0D);
        }
    }

    private void playTone(String deviceId) {
        SourceDataLine line = null;
        try {
            line = openPlaybackLine(deviceId);
            if (line == null) return;
            playbackLine = line;
            int sampleRate = (int) OUTPUT_FORMAT.getSampleRate();
            int totalSamples = sampleRate / 2;
            byte[] buffer = new byte[2048];
            int sample = 0;
            while (sample < totalSamples && !Thread.currentThread().isInterrupted()) {
                int samplesInBuffer = Math.min(buffer.length / 2, totalSamples - sample);
                for (int i = 0; i < samplesInBuffer; i++, sample++) {
                    double envelope = Math.min(1.0D, Math.min(sample / 800.0D,
                            (totalSamples - sample) / 1800.0D));
                    short value = (short) (Math.sin(2.0D * Math.PI * 440.0D * sample / sampleRate)
                            * 12000.0D * envelope);
                    buffer[i * 2] = (byte) (value & 0xff);
                    buffer[i * 2 + 1] = (byte) ((value >>> 8) & 0xff);
                }
                line.write(buffer, 0, samplesInBuffer * 2);
                publishOutput(computeRms(buffer, samplesInBuffer * 2));
            }
            line.drain();
        } catch (RuntimeException ignored) {
            // A playback device can disappear between enumeration and opening.
        } finally {
            if (line != null) {
                try { line.stop(); } catch (Exception ignored) { }
                try { line.close(); } catch (Exception ignored) { }
            }
            if (playbackLine == line) playbackLine = null;
            publishOutput(0.0D);
        }
    }

    private TargetDataLine openCaptureLine(String deviceId) {
        for (Mixer mixer : candidateMixers(deviceId, true)) {
            for (float sampleRate : SAMPLE_RATES) {
                AudioFormat format = pcmFormat(sampleRate);
                try {
                    DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                    if (!mixer.isLineSupported(info)) continue;
                    TargetDataLine line = (TargetDataLine) mixer.getLine(info);
                    line.open(format);
                    line.start();
                    return line;
                } catch (Exception ignored) {
                    // Try the next format or mixer.
                }
            }
        }
        return null;
    }

    private SourceDataLine openPlaybackLine(String deviceId) {
        for (Mixer mixer : candidateMixers(deviceId, false)) {
            try {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, OUTPUT_FORMAT);
                if (!mixer.isLineSupported(info)) continue;
                SourceDataLine line = (SourceDataLine) mixer.getLine(info);
                line.open(OUTPUT_FORMAT);
                line.start();
                return line;
            } catch (Exception ignored) {
                // Try the next output mixer.
            }
        }
        return null;
    }

    private static List<Mixer> candidateMixers(String deviceId, boolean capture) {
        List<Mixer> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String selected = normalize(deviceId);
        if (selected.isEmpty()) {
            try {
                addMixer(result, seen, AudioSystem.getMixer(null), capture);
            } catch (Exception ignored) { }
        }
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (!selected.isEmpty() && !deviceKey(info).equals(selected)) continue;
            try {
                addMixer(result, seen, AudioSystem.getMixer(info), capture);
            } catch (Exception ignored) { }
        }
        if (result.isEmpty() && !selected.isEmpty()) {
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                try { addMixer(result, seen, AudioSystem.getMixer(info), capture); }
                catch (Exception ignored) { }
            }
        }
        return result;
    }

    private static void addMixer(List<Mixer> target, Set<String> seen, Mixer mixer, boolean capture) {
        if (mixer == null) return;
        String key = deviceKey(mixer.getMixerInfo());
        if (!seen.add(key)) return;
        Line.Info[] infos = capture ? mixer.getTargetLineInfo() : mixer.getSourceLineInfo();
        if (infos != null && infos.length > 0) target.add(mixer);
    }

    static List<AudioDevice> listCaptureDevices() {
        return listDevices(true);
    }

    static List<AudioDevice> listPlaybackDevices() {
        return listDevices(false);
    }

    private static List<AudioDevice> listDevices(boolean capture) {
        List<AudioDevice> result = new ArrayList<>();
        result.add(new AudioDevice("", "Default", true));
        Set<String> seen = new HashSet<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                Line.Info[] lines = capture ? mixer.getTargetLineInfo() : mixer.getSourceLineInfo();
                if (lines == null || lines.length == 0) continue;
                String id = deviceKey(info);
                if (!seen.add(id)) continue;
                result.add(new AudioDevice(id, info.getName(), false));
            } catch (Exception ignored) {
                // Some Windows virtual devices advertise a line but cannot be opened.
            }
        }
        Collections.sort(result.subList(1, result.size()), Comparator.comparing(AudioDevice::getDisplayName,
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static AudioFormat pcmFormat(float sampleRate) {
        return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, 1,
                2, sampleRate, false);
    }

    static double computeRms(byte[] data, int length) {
        if (data == null || length < 2) return 0.0D;
        int safeLength = Math.min(length, data.length) - (Math.min(length, data.length) % 2);
        if (safeLength <= 0) return 0.0D;
        double sum = 0.0D;
        int samples = safeLength / 2;
        for (int i = 0; i < safeLength; i += 2) {
            int value = (short) ((data[i] & 0xff) | (data[i + 1] << 8));
            double normalized = value / 32768.0D;
            sum += normalized * normalized;
        }
        return Math.min(1.0D, Math.sqrt(sum / samples) * 2.2D);
    }

    private static String deviceKey(Mixer.Info info) {
        return info.getName() + "\u001f" + info.getVendor() + "\u001f"
                + info.getDescription() + "\u001f" + info.getVersion();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private void publishCapture(double value) {
        captureLevel = Math.max(0.0D, Math.min(1.0D, value));
        voiceActivityDetector.update(captureLevel, System.nanoTime());
        try { captureLevelListener.accept(captureLevel); } catch (RuntimeException ignored) { }
    }

    private void publishOutput(double value) {
        outputLevel = Math.max(0.0D, Math.min(1.0D, value));
        try { outputLevelListener.accept(outputLevel); } catch (RuntimeException ignored) { }
    }

    private void closeCaptureLine() {
        TargetDataLine line = captureLine;
        captureLine = null;
        Thread thread = captureThread;
        captureThread = null;
        if (thread != null) thread.interrupt();
        if (line != null) {
            try { line.stop(); } catch (Exception ignored) { }
            try { line.close(); } catch (Exception ignored) { }
        }
        publishCapture(0.0D);
    }

    @Override
    public void close() {
        running = false;
        synchronized (captureLock) {
            closeCaptureLine();
        }
        Thread thread = playbackThread;
        playbackThread = null;
        if (thread != null) thread.interrupt();
        SourceDataLine line = playbackLine;
        playbackLine = null;
        if (line != null) {
            try { line.stop(); } catch (Exception ignored) { }
            try { line.close(); } catch (Exception ignored) { }
        }
        publishOutput(0.0D);
    }
}
