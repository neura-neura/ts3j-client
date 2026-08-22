package com.github.manevolent.ts3j.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Append-only UTF-8 chat history. Each server gets a SHA-256 named file so
 * host names, ports and separators cannot escape the selected data directory.
 * Messages are base64 encoded because TeamSpeak text may contain newlines or
 * tabs. Malformed records are ignored so one damaged line cannot hide the rest
 * of a user's history.
 */
final class FileChannelChatRepository implements ChannelChatRepository {
    private static final String HEADER = "# ts3j-client channel chat v1";
    private static final int MAX_MESSAGES_PER_CHANNEL = 100;

    private final Path directory;
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    FileChannelChatRepository(Path directory) {
        if (directory == null) throw new IllegalArgumentException("directory");
        this.directory = directory;
    }

    @Override
    public synchronized Map<Integer, List<ChannelTextMessage>> load(String serverId) throws IOException {
        if (serverId == null || serverId.trim().isEmpty()) return Collections.emptyMap();
        Path file = fileFor(serverId);
        if (!Files.exists(file)) return Collections.emptyMap();

        Map<Integer, List<ChannelTextMessage>> result = new HashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String[] fields = line.split("\\t", -1);
            if (fields.length != 6 || !"M".equals(fields[0])) continue;
            try {
                int channelId = Integer.parseInt(fields[1]);
                long epochSecond = Long.parseLong(fields[2]);
                int nano = Integer.parseInt(fields[3]);
                String sender = new String(decoder.decode(fields[4]), StandardCharsets.UTF_8);
                String message = new String(decoder.decode(fields[5]), StandardCharsets.UTF_8);
                List<ChannelTextMessage> messages = result.get(channelId);
                if (messages == null) {
                    messages = new ArrayList<>();
                    result.put(channelId, messages);
                }
                messages.add(new ChannelTextMessage(channelId, sender, message,
                        Instant.ofEpochSecond(epochSecond, nano)));
                while (messages.size() > MAX_MESSAGES_PER_CHANNEL) messages.remove(0);
            } catch (RuntimeException ignored) {
                // Ignore a malformed record and continue loading later messages.
            }
        }
        return result;
    }

    @Override
    public synchronized void append(String serverId, ChannelTextMessage message) throws IOException {
        if (serverId == null || serverId.trim().isEmpty()) return;
        if (message == null) throw new IllegalArgumentException("message");
        Files.createDirectories(directory);
        Path file = fileFor(serverId);
        if (!Files.exists(file)) {
            Files.write(file, (HEADER + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }
        Instant receivedAt = message.getReceivedAt() == null ? Instant.EPOCH : message.getReceivedAt();
        String line = "M\t" + message.getChannelId()
                + "\t" + receivedAt.getEpochSecond()
                + "\t" + receivedAt.getNano()
                + "\t" + encode(message.getSender())
                + "\t" + encode(message.getMessage())
                + System.lineSeparator();
        Files.write(file, line.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private String encode(String value) {
        return encoder.encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private Path fileFor(String serverId) throws IOException {
        return directory.resolve(sha256(serverId.trim()) + ".chat");
    }

    private static String sha256(String value) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA-256 no disponible", error);
        }
    }
}
