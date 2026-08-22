package com.github.manevolent.ts3j.client;

import com.github.manevolent.ts3j.api.Channel;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChannelClassificationTest {
    @Test
    public void opusCodecIsVoice() {
        Channel channel = channel("4");
        ChannelView view = TeamSpeakGateway.toChannelView(channel, null);

        assertTrue(view.isVoiceCapable());
        assertTrue(view.isTextCapable());
        assertEquals("OPUS_VOICE", view.getCodec());
    }

    @Test
    public void partialEventKeepsKnownVoiceClassification() {
        ChannelView previous = new ChannelView(1, 0, 0, "Default Channel", true, "OPUS_VOICE");
        Channel channel = channel(null);

        ChannelView view = TeamSpeakGateway.toChannelView(channel, previous);

        assertTrue(view.isVoiceCapable());
        assertTrue(view.isTextCapable());
        assertEquals("OPUS_VOICE", view.getCodec());
    }

    @Test
    public void missingCodecWithoutEvidenceIsNotVoice() {
        ChannelView view = TeamSpeakGateway.toChannelView(channel(null), null);

        assertFalse(view.isVoiceCapable());
        assertTrue(view.isTextCapable());
        assertEquals("UNKNOWN", view.getCodec());
    }

    private static Channel channel(String codec) {
        Map<String, String> map = new HashMap<>();
        map.put("cid", "1");
        map.put("pid", "0");
        map.put("channel_order", "0");
        map.put("channel_name", "Default Channel");
        if (codec != null) map.put("channel_codec", codec);
        return new Channel(map);
    }
}
