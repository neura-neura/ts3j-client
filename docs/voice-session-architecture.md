# Shared voice-session architecture

`TeamSpeakGateway` is the only class that imports ts3j event and socket types.
It translates `ClientJoinEvent` (`ctid`), `ClientLeaveEvent` (`cfid`) and
`ClientMovedEvent` (`cfid`/`ctid`) into `PresenceDelta` values. `ChannelListEvent`
is translated into a UI-safe `ChannelView`; no public ts3j class is modified.

`VoiceSessionCoordinator` applies those deltas through a
`VoiceSessionRepository`. `InMemoryVoiceSessionRepository` is deterministic
for tests and the demo. `FileVoiceSessionRepository` serializes one state file
under a sibling lock file, so a read-modify-write operation is atomic across
multiple app processes on the same filesystem. The persisted record contains:

- `serverId` and `channelId`;
- UTC `voiceSessionStart` (or an explicit unknown marker);
- the set of present TeamSpeak client IDs;
- bounded duplicate-event IDs and per-client sequence watermarks.

The reducer treats joins, leaves, and moves as set operations. A second user
does not replace the start; leaving one of several users does not stop it; an
empty channel deletes the record, allowing the next join to create a new
session. Per-client sequence watermarks make a late lower-sequence event a
no-op when an upstream source supplies a stable sequence. The live gateway
does not pretend that its local callback order is a server sequence: it uses a
per-process event source for duplicate IDs, idempotent sets, and reconciliation.
ts3j does not provide a server-side event sequence or historical occupied-channel
start. Its available client timestamps are per-server-connection values, and
`seconds_empty` only describes an empty channel; neither is a valid shared voice
session start. The gateway therefore reconciles from `listClients()` after
connect/reconnect and marks a previously unknown occupied channel as
`startKnown=false`. The UI calls this state “En curso” rather than reporting an
error. To make the exact start available even when the official client entered
first, keep one instance of this app connected (or use a TeamSpeak server
plugin/ServerQuery service) before the first user enters and let it write
the same shared state file. If a stale known or unknown record is reconciled
with a snapshot containing only this instance's client, the gateway treats that
local join as a fresh zero-to-one observation and starts a new exact timer. A
snapshot that still contains another user keeps the previous session start (or
remains unknown if it was never recorded). No client can recover the earlier
timestamp after the fact.

Use a shared file path (for example a network share) for instances on different
machines. A local path synchronizes processes on the same machine only.
