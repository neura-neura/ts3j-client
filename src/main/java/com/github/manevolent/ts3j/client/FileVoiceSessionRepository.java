package com.github.manevolent.ts3j.client;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small lock-protected state file for sharing session starts between app
 * instances. Put the file on a shared filesystem when clients run on
 * different machines.
 */
public final class FileVoiceSessionRepository implements VoiceSessionRepository {
    private static final String HEADER = "ts3j-voice-sessions-v1";

    private final Path statePath;
    private final Path lockPath;

    public FileVoiceSessionRepository(Path statePath) {
        if (statePath == null) throw new IllegalArgumentException("statePath");
        this.statePath = statePath.toAbsolutePath();
        if (this.statePath.getFileName() == null) throw new IllegalArgumentException("statePath must name a file");
        Path parent = this.statePath.getParent();
        this.lockPath = (parent == null ? this.statePath.getFileSystem().getPath(".") : parent)
                .resolve(this.statePath.getFileName().toString() + ".lock");
    }

    public Path getStatePath() {
        return statePath;
    }

    @Override
    public SessionSnapshot snapshot() {
        try {
            return withLock(new LockedOperation<SessionSnapshot>() {
                @Override
                public SessionSnapshot run(SessionState state) {
                    return state.copy().snapshot();
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read session state " + statePath, e);
        }
    }

    @Override
    public SessionSnapshot apply(final PresenceDelta delta) {
        try {
            return withLock(new LockedOperation<SessionSnapshot>() {
                @Override
                public SessionSnapshot run(SessionState state) throws IOException {
                    SessionState updated = SessionMutationEngine.apply(state, delta);
                    writeState(updated);
                    return updated.copy().snapshot();
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Cannot update session state " + statePath, e);
        }
    }

    @Override
    public void clear() {
        try {
            withLock(new LockedOperation<Void>() {
                @Override
                public Void run(SessionState state) throws IOException {
                    writeState(new SessionState());
                    return null;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Cannot clear session state " + statePath, e);
        }
    }

    private <T> T withLock(LockedOperation<T> operation) throws IOException {
        Path parent = lockPath.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (FileChannel lockChannel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = lockChannel.lock()) {
            SessionState state = readState();
            return operation.run(state);
        }
    }

    private SessionState readState() throws IOException {
        SessionState state = new SessionState();
        if (!Files.exists(statePath)) return state;

        List<String> lines = Files.readAllLines(statePath);
        if (lines.isEmpty() || !HEADER.equals(lines.get(0))) return state;
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line == null || line.trim().isEmpty()) continue;
            String[] parts = line.split("\\t", -1);
            try {
                if ("S".equals(parts[0]) && parts.length >= 7) {
                    String serverId = decode(parts[1]);
                    int channelId = Integer.parseInt(parts[2]);
                    Instant start = "?".equals(parts[3]) ? null : Instant.parse(parts[3]);
                    boolean known = "1".equals(parts[4]);
                    long revision = Long.parseLong(parts[5]);
                    Set<Integer> users = new LinkedHashSet<>();
                    if (!parts[6].isEmpty()) {
                        for (String user : parts[6].split(",")) {
                            if (!user.isEmpty()) users.add(Integer.parseInt(user));
                        }
                    }
                    if (!users.isEmpty()) {
                        SessionKey key = new SessionKey(serverId, channelId);
                        state.sessions.put(key, new VoiceRoomSession(serverId, channelId,
                                start, known, users, revision));
                        state.revision = Math.max(state.revision, revision);
                    }
                } else if ("E".equals(parts[0]) && parts.length >= 2) {
                    state.seenEventIds.add(decode(parts[1]));
                } else if ("Q".equals(parts[0]) && parts.length >= 3) {
                    state.lastSequences.put(decode(parts[1]), Long.parseLong(parts[2]));
                }
            } catch (RuntimeException ignored) {
                // A damaged individual row must not discard all other channels.
            }
        }
        while (state.seenEventIds.size() > 1024) {
            state.seenEventIds.remove(state.seenEventIds.iterator().next());
        }
        return state;
    }

    private void writeState(SessionState state) throws IOException {
        Path parent = statePath.getParent();
        if (parent != null) Files.createDirectories(parent);
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);

        List<Map.Entry<SessionKey, VoiceRoomSession>> sessions =
                new ArrayList<>(state.sessions.entrySet());
        Collections.sort(sessions, new Comparator<Map.Entry<SessionKey, VoiceRoomSession>>() {
            @Override
            public int compare(Map.Entry<SessionKey, VoiceRoomSession> left,
                               Map.Entry<SessionKey, VoiceRoomSession> right) {
                int server = left.getKey().getServerId().compareTo(right.getKey().getServerId());
                return server != 0 ? server
                        : Integer.compare(left.getKey().getChannelId(), right.getKey().getChannelId());
            }
        });
        for (Map.Entry<SessionKey, VoiceRoomSession> entry : sessions) {
            VoiceRoomSession session = entry.getValue();
            if (session.getPresentUsers().isEmpty()) continue;
            List<Integer> users = new ArrayList<>(session.getPresentUsers());
            Collections.sort(users);
            StringBuilder usersValue = new StringBuilder();
            for (int i = 0; i < users.size(); i++) {
                if (i > 0) usersValue.append(',');
                usersValue.append(users.get(i));
            }
            lines.add("S\t" + encode(session.getServerId()) + "\t"
                    + session.getChannelId() + "\t"
                    + (session.getVoiceSessionStart() == null ? "?"
                    : session.getVoiceSessionStart().toString()) + "\t"
                    + (session.isStartKnown() ? "1" : "0") + "\t"
                    + session.getRevision() + "\t" + usersValue);
        }
        for (String eventId : state.seenEventIds) {
            lines.add("E\t" + encode(eventId));
        }
        for (Map.Entry<String, Long> sequence : state.lastSequences.entrySet()) {
            lines.add("Q\t" + encode(sequence.getKey()) + "\t" + sequence.getValue());
        }

        Path temp = Files.createTempFile(parent == null ? statePath.getFileSystem().getPath(".") : parent,
                statePath.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, lines, java.nio.charset.StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temp, statePath, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, statePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private interface LockedOperation<T> {
        T run(SessionState state) throws IOException;
    }
}
