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
    private static final int MAX_PLC_PACKETS = 6; // bound recovery work to 120 ms at 20 ms/packet
    private static final int MAX_REORDER_PACKETS = 1;

    private final AudioDeviceService devices;
    private final Supplier<CodecType> codecSupplier;
    private final ToDoubleFunction<Integer> volumeDbSupplier;
    private final OpusEncoder encoder;
    private final Map<Integer, VoiceDecoderState> decoders = new HashMap<>();
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
                decoders.remove(packet.getClientId());
            }
            return;
        }
        final VoiceDecoderState state;
        synchronized (decoders) {
            VoiceDecoderState existing = decoders.get(packet.getClientId());
            if (existing == null) {
                try {
                    VoiceDecoderState created = new VoiceDecoderState(packet.getClientId(),
                            new OpusDecoder(48000, 1));
                    decoders.put(packet.getClientId(), created);
                    if (decoders.size() > 128) decoders.clear();
                    state = created;
                } catch (OpusException error) {
                    return;
                }
            } else {
                state = existing;
            }
        }
        decodeVoicePacket(packet, state);
    }

    /**
     * Decodes one ordered stream. TeamSpeak carries a 16-bit voice packet id;
     * a one-packet reorder cushion lets a late UDP packet arrive before we
     * declare it lost. Once the cushion is full, feed the exact missing
     * duration to Opus PLC instead of inserting a hard PCM discontinuity.
     * Concentus documents this null/zero-length decode path as its packet-loss
     * concealment mechanism.
     */
    private void decodeVoicePacket(PacketBody0Voice packet, VoiceDecoderState state) {
        synchronized (state) {
            int packetId = packet.getPacketId() & 0xffff;
            if (state.expectedPacketId < 0) {
                decodeOrderedPacket(packet, state);
                state.expectedPacketId = (packetId + 1) & 0xffff;
                drainReorderedPackets(state);
                return;
            }
            int distance = (packetId - state.expectedPacketId) & 0xffff;
            if (distance > 0x8000) return; // duplicate or late UDP packet
            if (!state.pendingPackets.containsKey(packetId)) {
                state.pendingPackets.put(packetId, packet);
            }
            drainReorderedPackets(state);
        }
    }

    private void drainReorderedPackets(VoiceDecoderState state) {
        while (true) {
            PacketBody0Voice next = state.pendingPackets.remove(state.expectedPacketId);
            if (next != null) {
                decodeOrderedPacket(next, state);
                state.expectedPacketId = (state.expectedPacketId + 1) & 0xffff;
                continue;
            }
            if (state.pendingPackets.size() <= MAX_REORDER_PACKETS) return;
            int nearestId = state.nearestPendingPacketId();
            if (nearestId < 0) return;
            int missing = (nearestId - state.expectedPacketId) & 0xffff;
            decodeLostPackets(state, missing);
            PacketBody0Voice recovered = state.pendingPackets.remove(nearestId);
            if (recovered == null) return;
            decodeOrderedPacket(recovered, state);
            state.expectedPacketId = (nearestId + 1) & 0xffff;
        }
    }

    private void decodeLostPackets(VoiceDecoderState state, int missing) {
        if (missing <= 0) return;
        if (missing > MAX_PLC_PACKETS || state.lastDecodedSamples <= 0) {
            state.decoder.resetState();
            return;
        }
        try {
            for (int i = 0; i < missing; i++) {
                short[] concealed = new short[Math.min(MAX_DECODED_SAMPLES,
                        state.lastDecodedSamples)];
                int concealedSamples = state.decoder.decode(null, 0, 0, concealed, 0,
                        concealed.length, false);
                if (concealedSamples > 0) {
                    applyGain(concealed, concealedSamples, volumeDb(state.clientId));
                    enqueueDecodedFrames(state.clientId, concealed, concealedSamples);
                }
            }
        } catch (OpusException ignored) {
            state.decoder.resetState();
        }
    }

    private void decodeOrderedPacket(PacketBody0Voice packet, VoiceDecoderState state) {
        try {
            short[] decoded = new short[MAX_DECODED_SAMPLES];
            byte[] data = packet.getCodecData();
            int samples = state.decoder.decode(data, 0, data.length, decoded, 0,
                    MAX_DECODED_SAMPLES, false);
            if (samples > 0) {
                applyGain(decoded, samples, volumeDb(state.clientId));
                enqueueDecodedFrames(state.clientId, decoded, samples);
                state.lastDecodedSamples = samples;
            }
        } catch (OpusException ignored) {
            // A malformed packet invalidates predictive state; the next valid
            // packet starts a clean Opus stream.
            state.decoder.resetState();
        }
    }

    private static final class VoiceDecoderState {
        private final int clientId;
        private final OpusDecoder decoder;
        private int expectedPacketId = -1;
        private int lastDecodedSamples = AudioDeviceService.VOICE_FRAME_SAMPLES;
        private final Map<Integer, PacketBody0Voice> pendingPackets = new HashMap<>();

        private VoiceDecoderState(int clientId, OpusDecoder decoder) {
            this.clientId = clientId;
            this.decoder = decoder;
        }

        private int nearestPendingPacketId() {
            int nearestId = -1;
            int nearestDistance = 0x10000;
            for (Integer candidate : pendingPackets.keySet()) {
                int distance = (candidate - expectedPacketId) & 0xffff;
                if (distance > 0 && distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestId = candidate;
                }
            }
            return nearestId;
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

    /**
     * Opus may return more than one 20 ms frame (for example after a delayed
     * packet). Keep the hardware scheduler's cadence fixed by splitting the
     * decoded data into exactly one TeamSpeak frame per queue item.
     */
    private void enqueueDecodedFrames(int clientId, short[] samples, int count) {
        int safeCount = Math.min(count, samples == null ? 0 : samples.length);
        for (int offset = 0; offset < safeCount; offset += AudioDeviceService.VOICE_FRAME_SAMPLES) {
            int frameSamples = Math.min(AudioDeviceService.VOICE_FRAME_SAMPLES, safeCount - offset);
            byte[] pcm = new byte[AudioDeviceService.VOICE_FRAME_BYTES];
            for (int i = 0; i < frameSamples; i++) {
                short value = samples[offset + i];
                pcm[i * 2] = (byte) (value & 0xff);
                pcm[i * 2 + 1] = (byte) ((value >>> 8) & 0xff);
            }
            devices.enqueuePlaybackFrame(clientId, pcm);
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
