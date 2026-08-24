<p align="center">
  <img src="installer/assets/ts3j-client.png" alt="ts3j-client logo" width="240">
</p>
<h1 align="center">ts3j-client</h1>

<p align="center">A compact Java desktop client for TeamSpeak 3, powered by ts3j.</p>

## ts3j library

TS3J is an open-source implementation of the reverse-engineered Teamspeak3 full server/client protocol, as an adaptation of Splamy's C# TS3Client source code.  You can find that here: https://github.com/Splamy/TS3AudioBot/.

A standalone proof-of-concept was created to wrap ts3j: https://github.com/Manevolent/ts3j-musicbot

# A note about Teamspeak 5

I don't believe TS3j will need any major adjustments to work in a future Teamspeak5 world, based on what I have been able to get my hands on with TS5.  I've verified that TS3j does in fact work alongside TS5 clients, all connected to a "TS3" server.  There may be missing features that TS5 brings to the table, but those can be solved through issues and enhancement requests as they are identified.

# Projects using TS3j

TS3j has gotten a decent amount of attention for helping Java developers directly interact with Teamspeak3 servers and pipe audio for music bots, etc.  Maybe you're looking to build your idea on top of TS3j, but it's already been shared open-source.  That could save you a bunch of time!

| Name                     | GitHub                                    | Description                                                                                                                                                                                                                                                                                                                                     |
|--------------------------|-------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| JeakBot Plugin Framework | https://github.com/jeakfrw/jeak-framework | The JeakBot-Framework connects to the TeamSpeak server using the TS3 sever query interface.  Java plugins can use the API to interact with the TeamSpeak server using the JeakBot-API.  Plugins can be programmed in a way that developers may be familiar from the Sponge plugin API for Minecraft as the projects idea is inspired by Sponge. |
| Manebot                  | https://github.com/Manevolent/manebot-ts3 | The reference implementation of the Teamspeak3 platform for Manebot, a multi-platform chatbot framework.  You can use this plugin to get Manebot to interact with your Teamspeak3 server(s).                                                                                                                                                    |

If you made a project with TS3j on GitHub and would like to help people find it, feel free to let me know/open an issue and I'll add it here for you.


# Maven

If you want the latest `-SNAPSHOT`:

```xml
<repositories>
	<repository>
	    <id>jitpack.io</id>
	    <url>https://jitpack.io</url>
	</repository>
</repositories>
<dependency>
    <groupId>com.github.manevolent</groupId>
    <artifactId>ts3j</artifactId>
    <version>-SNAPSHOT</version>
</dependency>
```

# Connection & Basic Setup

```java
client = new LocalTeamspeakClientSocket();

// Set up client
client.setIdentity(identity);
client.addListener(listener);
client.setNickname(nickname);

client.connect(
   new InetSocketAddress(
        InetAddress.getByName(address),
        port // UDP client port, Teamspeak3 client uses 9987
   ),
   password,
   10000L
);

// Subscribe to all channels
client.subscribeAll();

// Get list of clients
for (Client basicClient : client.listClients())
	recognizeClient(client.getClientInfo(basicClient.getId()));
```


Note that while `connect()` is processing, you'll receive channels registered and clients currently connected to the server.  It is important that you use the listener to collect these, and track their changes through the other listener event calls.

You can interact with the server using the commands on the `client` object similarly to TS3Query.

# Handling chat

```java
// TS3Listener interface
@Override
public void onTextMessage(TextMessageEvent textMessageEvent) {
	if (textMessageEvent.getInvokerId() == client.getClientId())
    		return;

	// Global chat example
	client.sendServerMessage("Echo!");
	
	// PM to the sender
	client.sendPrivateMessage(textMessageEvent.getInvokerId(), "Echo!");
}
```

# Sending audio

```java
// Microphone interface
public void write(float[] buffer, int len) {
	byte[] opusPacket = doOpusEncodingHere(buffer, len);

	packetQueue.add(opusPacket);
}

@Override
public boolean isReady() {
	return true;
}

@Override
public CodecType getCodec() {
	return CodecType.OPUS_MUSIC;
}

@Override
public byte[] provide() {
	try {
	    if (packetQueue.peek() == null)
		return new byte[0]; // underflow

	    OpusPacket packet = packetQueue.remove();

	    if (packet == null)
		return new byte[0]; // underflow

	    return packet.getBytes();
	} catch (NoSuchElementException ex) {
	    return new byte[0]; // signals the decoder on the clients to stop
	}
}
```
# Receiving audio

Refer to the `setVoiceHandler` and `setWhisperHandler` methods to supply a Consumer object to receive Voice and Whisper packets.  You will need to decode the packets yourself, and insert packet-loss-correction as needed.

Note that the first 5 packets starting a voice session are marked with the COMPRESSED flag.  The final voice packet, intended to singal to close your decoder and flush samples, is always empty (0-length byte array).

Manebot can do this in its TS3 plugin, which uses TS3j: https://github.com/Manevolent/manebot-ts3/tree/master/src/main/java/io/manebot/plugin/ts3/platform/audio/voice

# ts3j desktop client

This repository also contains a JavaFX desktop client under
`com.github.manevolent.ts3j.client`. It keeps the original ts3j API intact and
adds a gateway, an authoritative shared voice-session repository, and a compact
dark/light interface.

The normal build and tests are:

```text
mvn clean test
```

The desktop target requires JDK 17 or newer (the original ts3j sources remain
Java 8-compatible); Maven resolves the JavaFX 17 runtime for the host
platform.

To open the local preview without a TeamSpeak server:

```text
mvn javafx:run "-Djavafx.args=--demo"
```

For a Windows installer, use `installer/build-installer.ps1`. It first creates
the Java runtime app-image with `jpackage` and then packages that image into a
real Inno Setup 6 EXE. The installer is a normal Windows setup program, not a
7-Zip/SFX archive, and it includes desktop and Start menu shortcuts.

Example (PowerShell):

```powershell
powershell -ExecutionPolicy Bypass -File .\installer\build-installer.ps1 `
  -Version 1.0.1 `
  -Maven C:\path\to\mvn.cmd `
  -Jpackage C:\path\to\jpackage.exe `
  -InnoSetup 'C:\Program Files (x86)\Inno Setup 6\ISCC.exe'
```

The stable Inno Setup `AppId` and `UsePreviousAppDir` keep the last selected
folder for future reinstalls. On the first migration from the old MSI, the
wizard also reads its registered `InstallLocation` and offers that folder
automatically; the folder selector remains available if a different location
is desired.

Building the EXE requires JDK 17+, Maven, and Inno Setup 6 (the installed
application has no Inno Setup runtime dependency). The modern deliverable is
`dist\ts3j-client-<version>.exe`; older MSI/WiX artifacts are legacy builds.

For an Apple Silicon macOS installer, run the independent macOS build script:

```bash
./installer/build-installer-macos.sh 1.0.15
```

It requires JDK 17+, Maven, and the macOS command-line tools. The build runs the
test suite, creates a self-contained ARM64 `.app`, adds the microphone privacy
description, signs the app, and produces
`dist/ts3j-client-<version>-macos-aarch64.dmg`. The DMG contains the app and an
Applications shortcut for normal drag-and-drop installation; the installed app
does not require a separate Java installation. Without
`MACOS_SIGNING_IDENTITY`, the script uses an ad-hoc signature suitable for
local testing. Public distribution still requires an Apple Developer ID and
notarization.

The preview is explicit demo data. A real connection starts from the
`Connect` button and asks for host, an optional port, nickname, and password.
Leave the port empty to use TeamSpeak 3's standard port `9987`, as in the
official client when connecting with only an address.

The desktop no longer creates or persists a timer. The companion
`ts3j-session-timer` service runs beside the TeamSpeak server, owns the
zero-to-one/one-to-zero transitions, persists `voiceSessionStart`, and sends
an authoritative marker to every connected client. The desktop keeps only the
in-memory channel occupancy needed to render that server-provided start; the
old shared state-file field is retained only as a source-compatibility detail.

The connection form remembers the last host, port, nickname, and password for
the current operating-system user. These values are stored in the local Java
Preferences profile and the password is never written to application logs or
included in `ConnectionConfig.toString()`.

TeamSpeak voice transport is UDP. For a private address such as
`192.168.196.65`, the computer running the client must be on the same LAN/VPN
(for example the same ZeroTier network), and the server/firewall must allow
`9987/UDP`. A successful `ping` or an open TCP query port does not prove that
the TeamSpeak voice port is reachable. The client binds/connects its datagram
socket to the selected endpoint so Windows can associate the VPN route and
firewall state with the handshake; a timeout now reports this UDP-specific
diagnosis instead of suggesting a certificate.

The desktop theme is also stored in Java Preferences and is restored on the
next launch. The Preferences dialog provides a persistent language selector
with English as the default, plus Spanish and Simplified Chinese translations;
changing the language rebuilds the visible shell immediately. The generated
pastel iOS-style icon is available as the PNG master at
`installer/assets/ts3j-client.png`, as a multi-size Windows ICO at
`installer/assets/ts3j-client.ico`, and as a macOS ICNS at
`installer/assets/ts3j-client.icns`; the same PNG is bundled into the JavaFX
resources and used by the window chrome and system tray. The installer assigns
that ICO explicitly to both shortcuts, recreates stale shortcut files during
reinstall, and asks Windows to refresh its icon cache after setup.

Some TeamSpeak servers omit `channel_codec` from `channellist` even though the
channel is a normal voice channel. The desktop gateway requests `channelinfo`
for those partial records, so a channel such as `Default Channel` is classified
as voice from its authoritative codec; its text-chat entry remains independent
of that voice classification.

TeamSpeak channel chat is independent of the voice codec: a channel such as
`Default Channel` can appear in both the text and voice sections. The desktop
client now keeps that dual capability, receives `notifytextmessage` events, and
uses ts3j's `sendChannelMessage` for the composer. The server still remains the
authority for channel-chat permissions, so a denied message is shown as a
connection error instead of being presented as sent. The full-client protocol
may omit `target` from a channel notification; the gateway resolves that event
from the sender/subscribed channel and also shows an accepted outgoing message
locally when the server does not echo it back. The gateway also appends each
accepted message to a local per-server history under
`~/.ts3j-client/chat-history`; after reconnecting, the UI renders
that local history followed by `*** End of chat history` and then new live
messages. Messages sent while this application was offline are not invented:
only messages actually observed by this instance are stored.

The timer displayed by the desktop is the server-provided UTC `Instant`. The
server service counts real users (not its own ServerQuery connection), starts
on the first user, keeps the same start while anyone remains connected, and
clears it only after the last user leaves. TeamSpeak's
`client_lastconnected`, `connection_connected_time`, and `seconds_empty` fields
describe an individual server connection or how long an empty channel has been
empty; none is the start of the current occupied server session.

If the server-side timer service is not installed or cannot reach ServerQuery,
the historical snapshot is marked `startKnown=false`. The UI presents the
channel as `Active` with a neutral “Session active before connecting”
note, rather than inventing a numeric duration. The exact time becomes
available when the authoritative service sends its marker. The normal client
protocol cannot reconstruct a session that predates the service.

The desktop client exposes the TeamSpeak `clientupdate` controls for away
status, manual microphone mute (`client_input_muted`) and speakers/headphones
mute (`client_output_muted`). Their accepted state is reflected in the local
client row and in the other clients' channel lists through TeamSpeak events.
Voice is now routed through ts3j's real UDP packet transport: Java Sound
captures 48 kHz mono frames, the bundled pure-Java Concentus codec encodes
TeamSpeak Opus packets, and incoming packets are decoded per speaker into the
selected playback device. The mute flag stops outbound frames while the
speakers flag remains the server-authoritative playback preference.

Each voice user has a context menu (right click, or the keyboard context-menu
key when the row is focused) with a local volume modifier from -50.0 dB to
+20.0 dB. The value is persisted per server and TeamSpeak unique identifier;
it is intentionally not sent as a server command. The local playback bridge
applies this modifier after decoding each speaker's Opus frame, matching the
official client's per-user volume behavior without changing server audio.

The desktop shell allows only one running instance per operating-system user. Launching
the executable again sends a local focus request to the existing process and
restores its window (including from the tray). When the user is not in a voice
channel, the tray shows the application logo. Inside a voice channel it becomes
a compact microphone state indicator: red means muted, gray means unmuted and
quiet, and green means the local microphone meter currently detects speech.
Repeated samples in the same state do not repaint the native tray icon, and a
small hysteresis/hold window filters brief threshold crossings so speech gaps
or input noise do not make the indicator flash.

Preferences also include a combined local audio panel. It enumerates Java
Sound capture and playback devices, remembers the selected IDs, shows live
microphone and application-output meters, and can play a short test tone. The
same selected devices are used by the live voice bridge when connected. Opus
channels are supported directly; legacy Speex/CELT channels remain visible but
do not transmit until a compatible codec adapter is added. The microphone
meter is processed locally only while the app is open; its level is not
uploaded by this client.

The application preferences dialog also exposes platform-native startup and
close-to-tray behavior. Windows uses the current user's Startup folder; macOS
uses the current user's `~/Library/LaunchAgents` folder and opens in the menu
bar. Neither path requires administrator privileges. The tray/menu-bar menu can
restore the window or exit the application.

Voice notifications are enabled by default and can be disabled from
Preferences. The alert-volume slider in the same panel is persisted locally
and applies to system speech and the bundled cues. Fixed actions such as
mute, away, connect, and chat play the bundled cue first; dynamic events such as
a user joining or leaving a channel use Windows SAPI or macOS `say` so the
notification can include the server, channel, and nickname. The selected
English, Spanish, or Chinese interface language selects the corresponding
installed system voice when one is available. A small original cue pack is
bundled inside the application as a fallback, so notifications do not depend
on the official TeamSpeak client being installed. TeamSpeak's copyrighted
sound-pack files are not copied or redistributed.
On macOS, bundled WAV cues and generated speech are routed through the native
`afplay` CoreAudio player so they follow the active system output device.
The outbound chat cue is emitted as soon as the local send is queued and uses
a dedicated notification lane, so it is not delayed by a network round-trip or
by a longer announcement already being spoken.

Updates are available from the Preferences dialog. The client checks the
public GitHub release for `neura-neura/ts3j-client`, selects an EXE on Windows
or a DMG/PKG on macOS when a newer semantic version is available, verifies a
downloaded macOS installer, closes the running application completely, and
starts the installer. Assets for another operating system are never downloaded.

When the official TeamSpeak 3 client is installed, the desktop client reads the
active identity record from `%APPDATA%\TS3Client\settings.db` on Windows or
`~/Library/Application Support/TeamSpeak 3/settings.db` on macOS. It opens the
database in read-only mode and keeps the identity only in memory. This preserves the server
permissions associated with the official identity; passwords and other settings
are not imported. If that database is unavailable, the client falls back to its
own persisted identity and upgrades it to security level 8 before connecting.
The official client is therefore optional: on the first run without
`settings.db`, ts3j-client creates a new random identity at
`~/.ts3j-client/identity.ini`, keeps it stable for that computer, and reuses it
on later connections. A newly generated identity has no permissions inherited
from another TeamSpeak account, so it cannot by itself reveal channels that the
server does not allow it to list.
Some TeamSpeak servers allow a guest identity to connect while denying the
`channellist` or `clientlist` commands. In that case the client stays connected
in limited-visibility mode, keeps the channels and users delivered by live
events, and queries the current user's channel directly when permitted. It
does not fabricate hidden channels; grant the identity the server's channel
list permissions or import an authorized identity if the complete tree is
required.
