package com.github.manevolent.ts3j.client;

/** UI-safe client data copied from a ts3j Client wrapper. */
public final class ClientView {
    private final int id;
    private final int channelId;
    private final String nickname;
    private final String uniqueIdentifier;
    private final int type;
    private final boolean inputMuted;
    private final boolean outputMuted;
    private final boolean away;

    public ClientView(int id, int channelId, String nickname, int type,
                      boolean inputMuted, boolean outputMuted) {
        this(id, channelId, nickname, "", type, inputMuted, outputMuted, false);
    }

    public ClientView(int id, int channelId, String nickname, int type,
                      boolean inputMuted, boolean outputMuted, boolean away) {
        this(id, channelId, nickname, "", type, inputMuted, outputMuted, away);
    }

    public ClientView(int id, int channelId, String nickname, String uniqueIdentifier, int type,
                      boolean inputMuted, boolean outputMuted, boolean away) {
        this.id = id;
        this.channelId = channelId;
        this.nickname = nickname == null || nickname.isEmpty() ? "Usuario " + id : nickname;
        this.uniqueIdentifier = uniqueIdentifier == null ? "" : uniqueIdentifier;
        this.type = type;
        this.inputMuted = inputMuted;
        this.outputMuted = outputMuted;
        this.away = away;
    }

    public int getId() { return id; }
    public int getChannelId() { return channelId; }
    public String getNickname() { return nickname; }
    public String getUniqueIdentifier() { return uniqueIdentifier; }
    public String getStableIdentity() {
        return uniqueIdentifier.isEmpty() ? "nickname:" + nickname : "uid:" + uniqueIdentifier;
    }
    public int getType() { return type; }
    public boolean isInputMuted() { return inputMuted; }
    public boolean isOutputMuted() { return outputMuted; }
    public boolean isAway() { return away; }

    ClientView withInputMuted(boolean value) {
        return new ClientView(id, channelId, nickname, uniqueIdentifier, type, value, outputMuted, away);
    }

    ClientView withOutputMuted(boolean value) {
        return new ClientView(id, channelId, nickname, uniqueIdentifier, type, inputMuted, value, away);
    }

    ClientView withAway(boolean value) {
        return new ClientView(id, channelId, nickname, uniqueIdentifier, type, inputMuted, outputMuted, value);
    }
}
