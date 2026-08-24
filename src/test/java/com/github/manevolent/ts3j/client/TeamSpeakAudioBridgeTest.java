package com.github.manevolent.ts3j.client;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusDecoder;
import io.github.jaredmdobson.concentus.OpusEncoder;
import io.github.jaredmdobson.concentus.OpusSignal;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TeamSpeakAudioBridgeTest {
    @Test
    public void opusVoiceFrameRoundTripsAsPcm() throws Exception {
        OpusEncoder encoder = new OpusEncoder(48000, 1, OpusApplication.OPUS_APPLICATION_VOIP);
        encoder.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
        short[] input = new short[AudioDeviceService.VOICE_FRAME_SAMPLES];
        for (int i = 0; i < input.length; i++) {
            input[i] = (short) (Math.sin(i * 0.08D) * 12000.0D);
        }
        byte[] packet = new byte[4000];
        int packetLength = encoder.encode(input, 0, input.length, packet, 0, packet.length);
        assertTrue(packetLength > 0);

        OpusDecoder decoder = new OpusDecoder(48000, 1);
        short[] output = new short[5760];
        int decodedSamples = decoder.decode(packet, 0, packetLength, output, 0,
                output.length, false);
        assertEquals(AudioDeviceService.VOICE_FRAME_SAMPLES, decodedSamples);
        assertTrue(Math.abs(output[100]) > 10);
    }

    @Test
    public void pcmConversionUsesTeamSpeakLittleEndianFrames() {
        byte[] pcm = new byte[AudioDeviceService.VOICE_FRAME_BYTES];
        pcm[0] = 0x34;
        pcm[1] = 0x12;
        pcm[2] = (byte) 0xcd;
        pcm[3] = (byte) 0xab;
        short[] samples = TeamSpeakAudioBridge.pcmToShorts(pcm);
        assertEquals((short) 0x1234, samples[0]);
        assertEquals((short) 0xabcd, samples[1]);
        byte[] roundTrip = TeamSpeakAudioBridge.shortsToPcm(samples, 2);
        assertEquals(0x34, roundTrip[0] & 0xff);
        assertEquals(0x12, roundTrip[1] & 0xff);
        assertEquals(0xcd, roundTrip[2] & 0xff);
        assertEquals(0xab, roundTrip[3] & 0xff);
    }

    @Test
    public void opusDecoderCanConcealARecoverableLostVoiceFrame() throws Exception {
        OpusEncoder encoder = new OpusEncoder(48000, 1, OpusApplication.OPUS_APPLICATION_VOIP);
        encoder.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
        short[] input = new short[AudioDeviceService.VOICE_FRAME_SAMPLES];
        for (int i = 0; i < input.length; i++) {
            input[i] = (short) (Math.sin(i * 0.06D) * 9000.0D);
        }
        byte[] first = new byte[4000];
        byte[] second = new byte[4000];
        int firstLength = encoder.encode(input, 0, input.length, first, 0, first.length);
        int secondLength = encoder.encode(input, 0, input.length, second, 0, second.length);

        OpusDecoder decoder = new OpusDecoder(48000, 1);
        short[] decoded = new short[AudioDeviceService.VOICE_FRAME_SAMPLES];
        assertEquals(AudioDeviceService.VOICE_FRAME_SAMPLES,
                decoder.decode(first, 0, firstLength, decoded, 0, decoded.length, false));
        assertEquals(AudioDeviceService.VOICE_FRAME_SAMPLES,
                decoder.decode(null, 0, 0, decoded, 0, decoded.length, false));
        assertEquals(AudioDeviceService.VOICE_FRAME_SAMPLES,
                decoder.decode(second, 0, secondLength, decoded, 0, decoded.length, false));
    }
}
