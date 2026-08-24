package com.github.manevolent.ts3j.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the public GitHub release and downloads its installer. */
final class UpdateService {
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
    private static final Pattern TAG_PATTERN = Pattern.compile(
            "\\\"tag_name\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DOWNLOAD_PATTERN = Pattern.compile(
            "\\\"browser_download_url\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_VERSION_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)*)");

    UpdateInfo checkLatest() throws IOException {
        String json = requestText(new URL(AppVersion.LATEST_RELEASE_API));
        return parseLatestRelease(json);
    }

    Path download(UpdateInfo update, ProgressListener listener) throws IOException {
        if (update == null) throw new IllegalArgumentException("update");
        if (update.getDownloadUrl() == null || update.getDownloadUrl().trim().isEmpty()) {
            throw new IOException("GitHub release did not include a compatible installer asset.");
        }
        URL url = new URL(update.getDownloadUrl());
        validateDownloadUrl(url);
        Path destination = Files.createTempFile("ts3j-client-update-", installerSuffix(update));
        HttpURLConnection connection = null;
        boolean complete = false;
        try {
            connection = open(url);
            try (InputStream input = responseStream(connection);
                 OutputStream output = Files.newOutputStream(destination)) {
                long total = connection.getContentLengthLong();
                long downloaded = 0L;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    output.write(buffer, 0, read);
                    downloaded += read;
                    if (listener != null && total > 0L) {
                        listener.onProgress(Math.min(1.0D, downloaded / (double) total));
                    }
                }
                complete = true;
                if (listener != null) listener.onProgress(1.0D);
                return destination;
            }
        } finally {
            if (connection != null) connection.disconnect();
            if (!complete) Files.deleteIfExists(destination);
        }
    }

    static UpdateInfo parseLatestRelease(String json) throws IOException {
        return parseLatestRelease(json, System.getProperty("os.name", ""));
    }

    static UpdateInfo parseLatestRelease(String json, String osName) throws IOException {
        if (json == null || json.trim().isEmpty()) throw new IOException("GitHub returned an empty release response.");
        String tag = firstMatch(TAG_PATTERN, json);
        String downloadUrl = null;
        String fallbackUrl = null;
        InstallerPlatform platform = installerPlatform(osName);
        Matcher matcher = DOWNLOAD_PATTERN.matcher(json);
        while (matcher.find()) {
            String candidate = unescapeJson(matcher.group(1));
            String normalizedCandidate = candidate.toLowerCase(Locale.ROOT);
            if (platform == InstallerPlatform.WINDOWS && normalizedCandidate.endsWith(".exe")) {
                downloadUrl = candidate;
                break;
            }
            if (platform == InstallerPlatform.MAC) {
                if (normalizedCandidate.endsWith(".dmg")) {
                    downloadUrl = candidate;
                    break;
                }
                if (fallbackUrl == null && normalizedCandidate.endsWith(".pkg")) {
                    fallbackUrl = candidate;
                }
            }
        }
        if (downloadUrl == null) downloadUrl = fallbackUrl;
        if (tag == null || tag.trim().isEmpty()) throw new IOException("GitHub release did not include a version tag.");
        String version = normalizeVersionTag(unescapeJson(tag));
        // The release version remains useful even when that release predates
        // this platform's installer. This lets an up-to-date Mac correctly
        // report "latest" instead of failing because the same release only
        // contains the legacy Windows asset.
        if (downloadUrl == null) return new UpdateInfo(version, "", null);
        URL asset;
        try {
            asset = new URL(downloadUrl);
            validateDownloadUrl(asset);
        } catch (Exception error) {
            throw new IOException("GitHub release contains an invalid installer URL.", error);
        }
        String path = asset.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        try {
            name = URLDecoder.decode(name, "UTF-8");
        } catch (Exception ignored) {
            // The URL path is still a usable fallback name.
        }
        return new UpdateInfo(version, name, downloadUrl);
    }

    static List<String> launchCommand(Path installer) {
        return launchCommand(installer, System.getProperty("os.name", ""));
    }

    static List<String> launchCommand(Path installer, String osName) {
        if (installer == null) throw new IllegalArgumentException("installer");
        String path = installer.toAbsolutePath().toString();
        InstallerPlatform platform;
        try {
            platform = installerPlatform(osName);
        } catch (IOException error) {
            throw new IllegalArgumentException(error.getMessage(), error);
        }
        return platform == InstallerPlatform.MAC
                ? Arrays.asList("/usr/bin/open", path)
                : Collections.singletonList(path);
    }

    void verifyInstaller(Path installer) throws IOException {
        List<String> command = verificationCommand(installer,
                System.getProperty("os.name", ""));
        if (command.isEmpty()) return;
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            if (!process.waitFor(60L, TimeUnit.SECONDS)) {
                throw new IOException("Timed out while verifying the downloaded installer.");
            }
            if (process.exitValue() != 0) {
                throw new IOException("The downloaded installer failed macOS integrity checks.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Installer verification was interrupted.", interrupted);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    static List<String> verificationCommand(Path installer, String osName) {
        if (installer == null) throw new IllegalArgumentException("installer");
        InstallerPlatform platform;
        try {
            platform = installerPlatform(osName);
        } catch (IOException error) {
            throw new IllegalArgumentException(error.getMessage(), error);
        }
        if (platform == InstallerPlatform.WINDOWS) return Collections.emptyList();
        String path = installer.toAbsolutePath().toString();
        String name = installer.getFileName() == null
                ? "" : installer.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".dmg")) return Arrays.asList("/usr/bin/hdiutil", "verify", path);
        if (name.endsWith(".pkg")) {
            return Arrays.asList("/usr/sbin/pkgutil", "--check-signature", path);
        }
        throw new IllegalArgumentException("Unsupported macOS installer type: " + name);
    }

    static String installerSuffix(UpdateInfo update) throws IOException {
        if (update == null) throw new IllegalArgumentException("update");
        String name = update.getAssetName() == null
                ? "" : update.getAssetName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".exe")) return ".exe";
        if (name.endsWith(".dmg")) return ".dmg";
        if (name.endsWith(".pkg")) return ".pkg";
        throw new IOException("Update does not identify a compatible installer type.");
    }

    static boolean isNewer(String candidate, String current) {
        return compareVersions(candidate, current) > 0;
    }

    static int compareVersions(String left, String right) {
        int[] a = numericVersion(left);
        int[] b = numericVersion(right);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return av < bv ? -1 : 1;
        }
        return 0;
    }

    private static int[] numericVersion(String value) {
        if (value == null) return new int[] {0};
        Matcher matcher = NUMERIC_VERSION_PATTERN.matcher(value.trim());
        if (!matcher.find()) return new int[] {0};
        String[] pieces = matcher.group(1).split("\\.");
        int[] result = new int[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            try {
                result[i] = Integer.parseInt(pieces[i]);
            } catch (NumberFormatException ignored) {
                result[i] = 0;
            }
        }
        return result;
    }

    private static String normalizeVersionTag(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.regionMatches(true, 0, "v", 0, 1)
                ? trimmed.substring(1) : trimmed;
    }

    private static InstallerPlatform installerPlatform(String osName) throws IOException {
        String normalized = osName == null ? "" : osName.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return InstallerPlatform.MAC;
        }
        if (normalized.startsWith("windows")) {
            return InstallerPlatform.WINDOWS;
        }
        throw new IOException("Updates are not supported on this operating system.");
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? unescapeJson(matcher.group(1)) : null;
    }

    private static String unescapeJson(String value) {
        if (value == null || value.indexOf('\\') < 0) return value;
        return value.replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String requestText(URL url) throws IOException {
        HttpURLConnection connection = open(url);
        try (InputStream input = responseStream(connection)) {
            return readUtf8(input);
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(URL url) throws IOException {
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("Only HTTPS update endpoints are supported.");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", AppVersion.PRODUCT_NAME + "/" + AppVersion.VERSION);
        return connection;
    }

    private static InputStream responseStream(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            closeQuietly(connection.getErrorStream());
            throw new IOException("GitHub returned HTTP " + code + ".");
        }
        return connection.getInputStream();
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void closeQuietly(InputStream input) {
        if (input == null) return;
        try {
            input.close();
        } catch (IOException ignored) { }
    }

    private static void validateDownloadUrl(URL url) throws IOException {
        String host = url.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(url.getProtocol())
                || !(host.equals("github.com") || host.endsWith(".github.com")
                || host.equals("githubusercontent.com") || host.endsWith(".githubusercontent.com"))) {
            throw new IOException("Installer URL is not a trusted GitHub HTTPS URL.");
        }
    }

    interface ProgressListener {
        void onProgress(double fraction);
    }

    private enum InstallerPlatform {
        WINDOWS,
        MAC
    }

    static final class UpdateInfo {
        private final String version;
        private final String assetName;
        private final String downloadUrl;

        UpdateInfo(String version, String assetName, String downloadUrl) {
            this.version = version;
            this.assetName = assetName;
            this.downloadUrl = downloadUrl;
        }

        String getVersion() { return version; }
        String getAssetName() { return assetName; }
        String getDownloadUrl() { return downloadUrl; }
    }
}
