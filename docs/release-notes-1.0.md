# ts3j-client 1.0

The first public desktop release of ts3j-client.

## Highlights

- TeamSpeak 3 desktop client built on the existing ts3j API.
- Shared voice-channel session timers backed by persisted UTC state.
- Voice and text channel navigation with local chat history.
- Persistent English, Spanish, and Simplified Chinese preferences.
- Away, microphone mute, and speaker mute controls.
- Per-user local volume controls, tray behavior, and Windows startup option.
- Bundled notification cues and configurable alert volume.
- GitHub-based update checking from Preferences.
- A real Inno Setup installer with a remembered installation directory and
  desktop/Start menu shortcuts.

## Installer

Download `ts3j-client-1.0.exe`, run it, and follow the setup wizard. The
installer includes its own Java runtime and does not require Java to be
installed separately. The SHA-256 checksum is:

`2DDF0E8CF093C57DCFB353CF17BF73F984F8F5FB0D886B783659EE56A9CFCB1E`

The update button in Preferences downloads future Windows installers from
this repository's public releases, closes the current client, and launches
the new installer.
