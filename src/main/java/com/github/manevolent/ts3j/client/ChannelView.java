package com.github.manevolent.ts3j.client;

/** UI-safe channel data copied from a ts3j Channel wrapper. */
public final class ChannelView {
    private final int id;
    private final int parentId;
    private final int order;
    private final String name;
    private final boolean voiceCapable;
    private final boolean textCapable;
    private final String codec;

    public ChannelView(int id, int parentId, int order, String name,
                       boolean voiceCapable, String codec) {
        this(id, parentId, order, name, voiceCapable, true, codec);
    }

    public ChannelView(int id, int parentId, int order, String name,
                       boolean voiceCapable, boolean textCapable, String codec) {
        this.id = id;
        this.parentId = parentId;
        this.order = order;
        this.name = name == null || name.isEmpty() ? "Canal " + id : name;
        this.voiceCapable = voiceCapable;
        this.textCapable = textCapable;
        this.codec = codec == null ? "" : codec;
    }

    public int getId() { return id; }
    public int getParentId() { return parentId; }
    public int getOrder() { return order; }
    public String getName() { return name; }
    public boolean isVoiceCapable() { return voiceCapable; }
    /** TeamSpeak channel chat is independent of the channel voice codec. */
    public boolean isTextCapable() { return textCapable; }
    public String getCodec() { return codec; }
}
