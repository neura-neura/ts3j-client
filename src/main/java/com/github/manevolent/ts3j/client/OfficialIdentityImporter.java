package com.github.manevolent.ts3j.client;

import com.github.manevolent.ts3j.identity.LocalIdentity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads the active TeamSpeak 3 identity from the official client's local
 * settings database. The identity is returned in memory and the database is
 * never modified; passwords and other settings are not queried.
 */
final class OfficialIdentityImporter {
    private static final int RECORD_UUID_FIELD = 2;
    private static final int RECORD_IDENTITY_FIELD = 17;
    private static final int IDENTITY_EXPORT_FIELD = 1;

    private OfficialIdentityImporter() { }

    static Optional<LocalIdentity> tryLoadDefault() {
        return tryLoad(defaultSettingsPath(System.getProperty("os.name", ""),
                System.getenv("APPDATA"), System.getProperty("user.home")));
    }

    static Path defaultSettingsPath(String osName, String appData, String userHome) {
        String normalizedOs = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (normalizedOs.contains("mac")) {
            return macSettingsPath(userHome);
        }
        return windowsSettingsPath(appData, userHome);
    }

    static Path windowsSettingsPath(String appData, String userHome) {
        return appData == null || appData.trim().isEmpty()
                ? Paths.get(userHome, "AppData", "Roaming", "TS3Client", "settings.db")
                : Paths.get(appData, "TS3Client", "settings.db");
    }

    static Path macSettingsPath(String userHome) {
        return Paths.get(userHome, "Library", "Application Support", "TeamSpeak 3",
                "settings.db");
    }

    static Optional<LocalIdentity> tryLoad(Path settingsPath) {
        if (settingsPath == null || !Files.isRegularFile(settingsPath)) return Optional.empty();
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + settingsPath.toAbsolutePath().toUri() + "?mode=ro")) {
                String preferredUuid = preferredIdentityUuid(connection);
                IdentityRecord fallback = null;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT value FROM ProtobufItems ORDER BY key");
                     ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        IdentityRecord record = parseRecord(result.getBytes(1));
                        if (record == null || record.identityExport == null) continue;
                        if (fallback == null) fallback = record;
                        if (preferredUuid != null && preferredUuid.equals(record.uuid)) {
                            LocalIdentity identity = readIdentity(record.identityExport);
                            if (identity != null) return Optional.of(identity);
                        }
                    }
                }
                if (fallback != null) {
                    LocalIdentity identity = readIdentity(fallback.identityExport);
                    if (identity != null) return Optional.of(identity);
                }
            }
        } catch (Exception ignored) {
            // A missing driver, locked/old database, or changed TS3 format must
            // not prevent the app's own identity fallback from working.
        }
        return Optional.empty();
    }

    private static String preferredIdentityUuid(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM Connecting WHERE key = 'LastUsedServerIdentityUuid'");
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getString(1) : null;
        }
    }

    static LocalIdentity readIdentityRecord(byte[] value, String preferredUuid)
            throws IOException {
        IdentityRecord record = parseRecord(value);
        if (record == null || record.identityExport == null) return null;
        if (preferredUuid != null && !preferredUuid.equals(record.uuid)) return null;
        return readIdentity(record.identityExport);
    }

    private static LocalIdentity readIdentity(String identityExport) {
        try {
            String ini = "[Identity]\r\nidentity=\"" + identityExport + "\"\r\n";
            return LocalIdentity.read(new ByteArrayInputStream(
                    ini.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException ignored) {
            return null;
        }
    }

    private static IdentityRecord parseRecord(byte[] value) {
        if (value == null) return null;
        String uuid = null;
        String identityExport = null;
        int offset = 0;
        while (offset < value.length) {
            Varint tag = readVarint(value, offset);
            if (tag == null) return null;
            offset = tag.next;
            int field = (int) (tag.value >>> 3);
            int wireType = (int) (tag.value & 7);
            if (wireType == 2) {
                Varint length = readVarint(value, offset);
                if (length == null || length.value > Integer.MAX_VALUE) return null;
                offset = length.next;
                int end = offset + (int) length.value;
                if (end < offset || end > value.length) return null;
                byte[] payload = new byte[end - offset];
                System.arraycopy(value, offset, payload, 0, payload.length);
                if (field == RECORD_UUID_FIELD) {
                    uuid = new String(payload, StandardCharsets.UTF_8);
                } else if (field == RECORD_IDENTITY_FIELD) {
                    identityExport = parseIdentityExport(payload);
                }
                offset = end;
            } else {
                offset = skip(value, offset, wireType);
                if (offset < 0) return null;
            }
        }
        return new IdentityRecord(uuid, identityExport);
    }

    private static String parseIdentityExport(byte[] nested) {
        int offset = 0;
        while (offset < nested.length) {
            Varint tag = readVarint(nested, offset);
            if (tag == null) return null;
            offset = tag.next;
            int field = (int) (tag.value >>> 3);
            int wireType = (int) (tag.value & 7);
            if (wireType == 2) {
                Varint length = readVarint(nested, offset);
                if (length == null || length.value > Integer.MAX_VALUE) return null;
                offset = length.next;
                int end = offset + (int) length.value;
                if (end < offset || end > nested.length) return null;
                if (field == IDENTITY_EXPORT_FIELD) {
                    return new String(nested, offset, end - offset, StandardCharsets.UTF_8);
                }
                offset = end;
            } else {
                offset = skip(nested, offset, wireType);
                if (offset < 0) return null;
            }
        }
        return null;
    }

    private static int skip(byte[] value, int offset, int wireType) {
        switch (wireType) {
            case 0:
                Varint varint = readVarint(value, offset);
                return varint == null ? -1 : varint.next;
            case 1:
                return offset + 8 <= value.length ? offset + 8 : -1;
            case 2:
                Varint length = readVarint(value, offset);
                if (length == null || length.value > Integer.MAX_VALUE) return -1;
                int end = length.next + (int) length.value;
                return end >= length.next && end <= value.length ? end : -1;
            case 5:
                return offset + 4 <= value.length ? offset + 4 : -1;
            default:
                return -1;
        }
    }

    private static Varint readVarint(byte[] value, int offset) {
        long result = 0L;
        int shift = 0;
        while (offset < value.length && shift < 64) {
            int current = value[offset++] & 0xff;
            result |= (long) (current & 0x7f) << shift;
            if ((current & 0x80) == 0) return new Varint(result, offset);
            shift += 7;
        }
        return null;
    }

    private static final class Varint {
        private final long value;
        private final int next;

        private Varint(long value, int next) {
            this.value = value;
            this.next = next;
        }
    }

    private static final class IdentityRecord {
        private final String uuid;
        private final String identityExport;

        private IdentityRecord(String uuid, String identityExport) {
            this.uuid = uuid;
            this.identityExport = identityExport;
        }
    }
}
