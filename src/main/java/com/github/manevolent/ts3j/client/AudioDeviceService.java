package com.github.manevolent.ts3j.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Java Sound bridge for the TeamSpeak voice transport. It owns the selected
 * devices, exposes fixed 48 kHz mono PCM frames for the Opus microphone and
 * consumes decoded PCM frames from incoming TeamSpeak voice packets. The
 * protocol/codec adapter lives in {@link TeamSpeakAudioBridge}; this class
 * remains responsible only for hardware I/O and level meters.
 */
final class AudioDeviceService implements AutoCloseable {
    private static final float[] SAMPLE_RATES = {48000.0F, 44100.0F, 16000.0F};
    private static final float[] PLAYBACK_SAMPLE_RATES = {48000.0F, 44100.0F};
    static final float VOICE_SAMPLE_RATE = 48000.0F;
    static final int VOICE_FRAME_SAMPLES = 960; // 20 ms at 48 kHz
    static final int VOICE_FRAME_BYTES = VOICE_FRAME_SAMPLES * 2;
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
    private volatile Thread testToneThread;
    private volatile double captureLevel;
    private volatile double outputLevel;
    private volatile boolean voiceCaptureReady;
    private final BlockingQueue<byte[]> captureFrames = new ArrayBlockingQueue<>(8);
    private final BlockingQueue<byte[]> playbackFrames = new ArrayBlockingQueue<>(32);
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

    boolean isVoiceCaptureReady() { return voiceCaptureReady; }

    byte[] pollCaptureFrame() {
        return captureFrames.poll();
    }

    void clearCaptureFrames() {
        captureFrames.clear();
    }

    void clearPlaybackFrames() {
        playbackFrames.clear();
    }

    void enqueuePlaybackFrame(byte[] pcm) {
        if (pcm == null || pcm.length == 0 || !running) return;
        byte[] copy = Arrays.copyOf(pcm, pcm.length);
        if (!playbackFrames.offer(copy)) {
            playbackFrames.poll();
            playbackFrames.offer(copy);
        }
    }

    boolean isVoiceDetected() {
        return voiceActivityDetector.isActive();
    }

    void start() {
        if (running) return;
        running = true;
        restartCapture();
        startPlayback();
    }

    void setCaptureDeviceId(String id) {
        captureDeviceId = normalize(id);
        clearCaptureFrames();
        if (running) restartCapture();
    }

    void setPlaybackDeviceId(String id) {
        playbackDeviceId = normalize(id);
        if (running) restartPlayback();
    }

    void playTestTone() {
        playTestTone(playbackDeviceId);
    }

    void playTestTone(String deviceId) {
        if (!running) return;
        Thread previous = testToneThread;
        if (previous != null) previous.interrupt();
        String selected = normalize(deviceId);
        if (!selected.isEmpty() && !selected.equals(playbackDeviceId)) {
            playbackDeviceId = selected;
            restartPlayback();
        }
        Thread next = new Thread(this::playTone, "ts3j-client-audio-test");
        next.setDaemon(true);
        testToneThread = next;
        next.start();
    }

    private void restartCapture() {
        synchronized (captureLock) {
            closeCaptureLine();
            if (!running) return;
            final TargetDataLine line = openCaptureLine(captureDeviceId);
            if (line == null) {
                voiceCaptureReady = false;
                publishCapture(0.0D);
                return;
            }
            captureLine = line;
            voiceCaptureReady = supportedSampleRate(line.getFormat().getSampleRate());
            Thread next = new Thread(() -> captureLoop(line), "ts3j-client-microphone-meter");
            next.setDaemon(true);
            captureThread = next;
            next.start();
        }
    }

    private void captureLoop(TargetDataLine line) {
        // Keep the attack responsive for short words while the state
        // hysteresis below prevents the extra samples from flashing the tray.
        byte[] buffer = new byte[4096];
        int sourceFrameBytes = Math.max(2, Math.round(
                line.getFormat().getSampleRate() * VOICE_FRAME_SAMPLES / VOICE_SAMPLE_RATE) * 2);
        byte[] frame = new byte[sourceFrameBytes];
        int frameFill = 0;
        try {
            while (running && captureLine == line && !Thread.currentThread().isInterrupted()) {
                int read = line.read(buffer, 0, buffer.length);
                if (read <= 0) continue;
                publishCapture(computeRms(buffer, read));
                if (!voiceCaptureReady) continue;
                int offset = 0;
                while (offset < read) {
                    int copy = Math.min(read - offset, frame.length - frameFill);
                    System.arraycopy(buffer, offset, frame, frameFill, copy);
                    offset += copy;
                    frameFill += copy;
                    if (frameFill == frame.length) {
                        byte[] ready = resamplePcm(frame, line.getFormat().getSampleRate(), VOICE_SAMPLE_RATE);
                        if (!captureFrames.offer(ready)) {
                            captureFrames.poll();
                            captureFrames.offer(ready);
                        }
                        frameFill = 0;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // A device can disappear while the user changes Windows audio settings.
        } finally {
            if (captureLine == line) {
                captureLine = null;
                try { line.stop(); } catch (Exception ignored) { }
                try { line.close(); } catch (Exception ignored) { }
            }
            voiceCaptureReady = false;
            publishCapture(0.0D);
        }
    }

    private void playTone() {
        try {
            // The normal playback loop owns the hardware line. Enqueue the
            // test signal so it follows the same selected device and level
            // meter as decoded TeamSpeak audio.
            int sampleRate = (int) VOICE_SAMPLE_RATE;
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
                enqueuePlaybackFrame(Arrays.copyOf(buffer, samplesInBuffer * 2));
                try { Thread.sleep(8L); } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // A playback device can disappear between enumeration and opening.
        } finally {
            if (testToneThread == Thread.currentThread()) testToneThread = null;
        }
    }

    private void startPlayback() {
        if (playbackThread != null && playbackThread.isAlive()) return;
        Thread next = new Thread(this::playbackLoop, "ts3j-client-playback");
        next.setDaemon(true);
        playbackThread = next;
        next.start();
    }

    private void restartPlayback() {
        SourceDataLine line = playbackLine;
        if (line != null) {
            try { line.stop(); } catch (Exception ignored) { }
            try { line.close(); } catch (Exception ignored) { }
        }
    }

    private void playbackLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            SourceDataLine line = openPlaybackLine(playbackDeviceId);
            if (line == null) {
                publishOutput(0.0D);
                try { Thread.sleep(250L); } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            playbackLine = line;
            try {
                while (running && playbackLine == line && !Thread.currentThread().isInterrupted()) {
                    byte[] pcm = playbackFrames.poll(200L, TimeUnit.MILLISECONDS);
                    if (pcm == null) {
                        publishOutput(0.0D);
                        continue;
                    }
                    byte[] output = resamplePcm(pcm, VOICE_SAMPLE_RATE,
                            line.getFormat().getSampleRate());
                    line.write(output, 0, output.length);
                    publishOutput(computeRms(output, output.length));
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException ignored) {
                // Retry after a device disappears or is reconfigured.
            } finally {
                if (playbackLine == line) playbackLine = null;
                try { line.stop(); } catch (Exception ignored) { }
                try { line.close(); } catch (Exception ignored) { }
                publishOutput(0.0D);
            }
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
            for (float sampleRate : PLAYBACK_SAMPLE_RATES) {
                AudioFormat format = pcmFormat(sampleRate);
                try {
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                    if (!mixer.isLineSupported(info)) continue;
                    SourceDataLine line = (SourceDataLine) mixer.getLine(info);
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

    private static boolean supportedSampleRate(float sampleRate) {
        for (float supported : SAMPLE_RATES) {
            if (Math.abs(sampleRate - supported) < 0.5F) return true;
        }
        return false;
    }

    /** Linear, mono, signed-16 little-endian resampling for device fallbacks. */
    static byte[] resamplePcm(byte[] pcm, float fromRate, float toRate) {
        if (pcm == null || pcm.length < 2) return new byte[0];
        if (Math.abs(fromRate - toRate) < 0.5F) return Arrays.copyOf(pcm, pcm.length);
        int inputSamples = pcm.length / 2;
        int outputSamples = Math.max(1, Math.round(inputSamples * toRate / fromRate));
        byte[] output = new byte[outputSamples * 2];
        for (int i = 0; i < outputSamples; i++) {
            double source = i * fromRate / toRate;
            int lower = Math.min(inputSamples - 1, (int) Math.floor(source));
            int upper = Math.min(inputSamples - 1, lower + 1);
            double fraction = source - lower;
            short lowerValue = readSample(pcm, lower);
            short upperValue = readSample(pcm, upper);
            int value = (int) Math.round(lowerValue + (upperValue - lowerValue) * fraction);
            short sample = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
            output[i * 2] = (byte) (sample & 0xff);
            output[i * 2 + 1] = (byte) ((sample >>> 8) & 0xff);
        }
        return output;
    }

    private static short readSample(byte[] pcm, int sample) {
        int offset = sample * 2;
        return (short) ((pcm[offset] & 0xff) | (pcm[offset + 1] << 8));
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
        Thread tone = testToneThread;
        testToneThread = null;
        if (tone != null) tone.interrupt();
        SourceDataLine line = playbackLine;
        playbackLine = null;
        if (line != null) {
            try { line.stop(); } catch (Exception ignored) { }
            try { line.close(); } catch (Exception ignored) { }
        }
        captureFrames.clear();
        playbackFrames.clear();
        publishOutput(0.0D);
    }
}
