package com.github.manevolent.ts3j.client;

import com.github.manevolent.ts3j.audio.Microphone;
import com.github.manevolent.ts3j.enums.CodecType;
import com.github.manevolent.ts3j.protocol.packet.PacketBody0Voice;
import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusException;
import io.github.jaredmdobson.concentus.OpusSignal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Connects Java Sound frames to the voice packets already implemented by
 * ts3j. TeamSpeak's default voice codecs are Opus; Concentus keeps the codec
 * portable on Windows and macOS without requiring a native DLL or an
 * installation of the official client.
 */
final class TeamSpeakAudioBridge implements Microphone, AutoCloseable {
    private static final int MAX_OPUS_PACKET_BYTES = 4000;
    private static final int MAX_DECODED_SAMPLES = 5760; // Opus' 120 ms maximum

    private final AudioDeviceService devices;
    private final Supplier<CodecType> codecSupplier;
    private final ToDoubleFunction<Integer> volumeDbSupplier;
    private final OpusEncoder encoder;
    private final Map<Integer, OpusDecoder> decoders = new HashMap<>();
    private volatile boolean muted;
    private volatile boolean outputMuted;

    TeamSpeakAudioBridge(AudioDeviceService devices, Supplier<CodecType> codecSupplier) {
        this(devices, codecSupplier, clientId -> 0.0D);
    }

    TeamSpeakAudioBridge(AudioDeviceService devices, Supplier<CodecType> codecSupplier,
                         ToDoubleFunction<Integer> volumeDbSupplier) {
        if (devices == null || codecSupplier == null) throw new IllegalArgumentException("audio");
        this.devices = devices;
        this.codecSupplier = codecSupplier;
        this.volumeDbSupplier = volumeDbSupplier == null ? clientId -> 0.0D : volumeDbSupplier;
        try {
            encoder = new OpusEncoder(48000, 1, OpusApplication.OPUS_APPLICATION_VOIP);
            encoder.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
            encoder.setBitrate(32000);
            encoder.setComplexity(5);
            encoder.setUseVBR(true);
            encoder.setUseConstrainedVBR(true);
        } catch (OpusException error) {
            throw new IllegalStateException("No se pudo inicializar el códec Opus", error);
        }
    }

    void setMuted(boolean muted) {
        this.muted = muted;
        if (muted) devices.clearCaptureFrames();
    }

    void setOutputMuted(boolean muted) {
        outputMuted = muted;
        if (muted) {
            devices.clearPlaybackFrames();
            synchronized (decoders) {
                decoders.clear();
            }
        }
    }

    @Override
    public boolean isMuted() {
        return muted;
    }

    @Override
    public boolean isReady() {
        return !muted && devices.isVoiceCaptureReady() && isOpusCodec(currentCodec());
    }

    @Override
    public CodecType getCodec() {
        CodecType codec = currentCodec();
        return isOpusCodec(codec) ? codec : CodecType.OPUS_VOICE;
    }

    @Override
    public byte[] provide() {
        if (!isReady()) return new byte[0];
        byte[] pcm = devices.pollCaptureFrame();
        // Keep the TS3 voice stream alive across a single delayed capture read
        // by sending a real encoded silence frame instead of a decoder reset.
        if (pcm == null) pcm = new byte[AudioDeviceService.VOICE_FRAME_BYTES];
        try {
            short[] samples = pcmToShorts(pcm);
            byte[] encoded = new byte[MAX_OPUS_PACKET_BYTES];
            int length = encoder.encode(samples, 0, AudioDeviceService.VOICE_FRAME_SAMPLES,
                    encoded, 0, encoded.length);
            return Arrays.copyOf(encoded, length);
        } catch (OpusException error) {
            return new byte[0];
        }
    }

    void handleVoicePacket(PacketBody0Voice packet) {
        if (outputMuted || packet == null || !isOpusCodec(packet.getCodecType())) return;
        byte[] data = packet.getCodecData();
        if (data == null || data.length == 0) {
            synchronized (decoders) {
                OpusDecoder decoder = decoders.get(packet.getClientId());
                if (decoder != null) decoder.resetState();
            }
            return;
        }
        final OpusDecoder decoder;
        synchronized (decoders) {
            OpusDecoder existing = decoders.get(packet.getClientId());
            if (existing == null) {
                try {
                    existing = new OpusDecoder(48000, 1);
                } catch (OpusException error) {
                    return;
                }
                decoders.put(packet.getClientId(), existing);
                // A restricted client can reuse numeric client ids after a
                // reconnect. Keep the cache bounded without sharing decoder
                // state between different active speakers.
                if (decoders.size() > 128) decoders.clear();
            }
            decoder = existing;
        }
        try {
            short[] decoded = new short[MAX_DECODED_SAMPLES];
            int samples = decoder.decode(data, 0, data.length, decoded, 0,
                    MAX_DECODED_SAMPLES, false);
            if (samples > 0) {
                applyGain(decoded, samples, volumeDb(packet.getClientId()));
                devices.enqueuePlaybackFrame(shortsToPcm(decoded, samples));
            }
        } catch (OpusException ignored) {
            // Ignore a malformed/lost frame; the next valid packet restores
            // the decoder state without taking down the TeamSpeak connection.
        }
    }

    private CodecType currentCodec() {
        try {
            CodecType codec = codecSupplier.get();
            return codec == null ? CodecType.OPUS_VOICE : codec;
        } catch (RuntimeException ignored) {
            return CodecType.OPUS_VOICE;
        }
    }

    private double volumeDb(int clientId) {
        try {
            double value = volumeDbSupplier.applyAsDouble(clientId);
            return Math.max(-50.0D, Math.min(20.0D, value));
        } catch (RuntimeException ignored) {
            return 0.0D;
        }
    }

    private static void applyGain(short[] samples, int count, double decibels) {
        if (samples == null || count <= 0 || Math.abs(decibels) < 0.01D) return;
        double gain = Math.pow(10.0D, decibels / 20.0D);
        int safeCount = Math.min(count, samples.length);
        for (int i = 0; i < safeCount; i++) {
            int value = (int) Math.round(samples[i] * gain);
            samples[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
        }
    }

    private static boolean isOpusCodec(CodecType codec) {
        return codec == CodecType.OPUS_VOICE || codec == CodecType.OPUS_MUSIC;
    }

    static short[] pcmToShorts(byte[] pcm) {
        short[] result = new short[AudioDeviceService.VOICE_FRAME_SAMPLES];
        if (pcm == null) return result;
        int bytes = Math.min(pcm.length, result.length * 2);
        for (int i = 0; i + 1 < bytes; i += 2) {
            result[i / 2] = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
        }
        return result;
    }

    static byte[] shortsToPcm(short[] samples, int count) {
        if (samples == null || count <= 0) return new byte[0];
        int safeCount = Math.min(count, samples.length);
        byte[] result = new byte[safeCount * 2];
        for (int i = 0; i < safeCount; i++) {
            short value = samples[i];
            result[i * 2] = (byte) (value & 0xff);
            result[i * 2 + 1] = (byte) ((value >>> 8) & 0xff);
        }
        return result;
    }

    @Override
    public void close() {
        synchronized (decoders) {
            decoders.clear();
        }
        devices.clearCaptureFrames();
    }
}
