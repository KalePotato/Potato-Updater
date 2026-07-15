package com.potato.updater.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.potato.updater.config.EndpointDefaults;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

public final class SeedSelfUpdater {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_SEED_CONFIG_URL = EndpointDefaults.seedConfigUrl();
    private static final String SEED_STATE_FILE = "seed_state.json";
    private static final String SEED_CONFIG_FILE = "seed_config.json";
    private static final String SEED_VERSION_FILE = "seed_version.json";
    private static final String SEED_JAR_NAME = "Potato_Seed.jar";
    private static final String PENDING_META_FILE = "seed_update.json";
    private static final String EXPECTED_PREMAIN_CLASS = "com.potato.seed.agent.PotatoSeedAgent";
    private static final int JSON_CONNECT_TIMEOUT_MS = 8_000;
    private static final int JSON_READ_TIMEOUT_MS = 8_000;
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 15_000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 30_000;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1_000L;
    private static final long APPLY_RETRY_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(3);
    private static final long APPLY_RETRY_DELAY_MS = 250L;
    private static final String USER_AGENT =
            "Mozilla/5.0 AppleWebKit/537.36 PotatoUpdaterSeedSelfUpdate/1.0";

    private final Path gameCoreDir;

    public SeedSelfUpdater(Path gameCoreDir) {
        this.gameCoreDir = gameCoreDir.toAbsolutePath().normalize();
    }

    public CheckResult checkForUpdate() throws Exception {
        String seedConfigUrl = readSeedConfigUrl();
        JsonObject seedJson = fetchJson(seedConfigUrl, "seed config");
        String updaterUrl = requiredString(seedJson, "Potato_Updater", "seed.json missing Potato_Updater");
        String seedVersionUrl = resolveSibling(updaterUrl, SEED_VERSION_FILE);

        JsonObject seedVersionJson = fetchJson(seedVersionUrl, "seed version");
        String remoteVersion = requiredString(seedVersionJson, "version", "seed_version.json missing version");
        String seedUrl = optionalString(seedVersionJson, "Potato_Seed");
        if (seedUrl.isBlank()) {
            seedUrl = resolveSibling(updaterUrl, SEED_JAR_NAME);
        }

        String localVersion = readLocalSeedVersion();
        boolean updateAvailable = !remoteVersion.equals(localVersion);
        return new CheckResult(updateAvailable, localVersion, remoteVersion, seedUrl, seedVersionUrl);
    }

    public PreparedUpdate preparePendingUpdate(CheckResult checkResult) throws Exception {
        if (checkResult == null || !checkResult.isUpdateAvailable()) {
            throw new IllegalArgumentException("No pending Seed update is available.");
        }

        Path pendingDir = gameCoreDir.resolve("A_Potato_Updater")
                .resolve("seed_update")
                .resolve("pending_" + Long.toHexString(System.nanoTime()));
        Path downloadTemp = pendingDir.resolve(SEED_JAR_NAME + ".download");
        Path seedJar = pendingDir.resolve(SEED_JAR_NAME);

        try {
            Files.createDirectories(pendingDir);
            downloadFile(checkResult.getSeedUrl(), downloadTemp, "seed jar");
            validateSeedJar(downloadTemp);
            moveReplacing(downloadTemp, seedJar);
            writePendingMeta(pendingDir, checkResult);
            return new PreparedUpdate(pendingDir, checkResult.getRemoteVersion());
        } catch (Exception e) {
            deleteRecursively(pendingDir);
            throw e;
        }
    }

    public static void applyPendingSeedUpdate(Path gameCoreDir, Path pendingDir) throws Exception {
        Path normalizedGameCore = gameCoreDir.toAbsolutePath().normalize();
        Path normalizedPending = pendingDir.toAbsolutePath().normalize();
        JsonObject meta = readJsonObject(normalizedPending.resolve(PENDING_META_FILE));
        String version = requiredString(meta, "version", "pending seed update missing version");

        Path sourceJar = normalizedPending.resolve(SEED_JAR_NAME);
        validateSeedJar(sourceJar);

        Path targetJar = normalizedGameCore.resolve(SEED_JAR_NAME);
        Path targetTemp = normalizedGameCore.resolve(SEED_JAR_NAME + ".updating");
        try {
            copyWithRetry(sourceJar, targetTemp);
            validateSeedJar(targetTemp);
            moveWithRetry(targetTemp, targetJar);
            writeLocalSeedVersion(normalizedGameCore, version);
            deleteRecursively(normalizedPending);
        } finally {
            Files.deleteIfExists(targetTemp);
        }
    }

    private String readSeedConfigUrl() {
        Path configPath = gameCoreDir.resolve("A_Potato_Seed").resolve(SEED_CONFIG_FILE);
        try {
            JsonObject config = readJsonObject(configPath);
            String configured = optionalString(config, "remoteConfigUrl");
            return configured.isBlank() ? DEFAULT_SEED_CONFIG_URL : configured;
        } catch (Exception ignored) {
            return DEFAULT_SEED_CONFIG_URL;
        }
    }

    private String readLocalSeedVersion() {
        try {
            JsonObject state = readJsonObject(seedStatePath(gameCoreDir));
            return optionalString(state, "currentSeedVersion");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void writeLocalSeedVersion(Path gameCoreDir, String version) throws IOException {
        Path statePath = seedStatePath(gameCoreDir);
        JsonObject state;
        try {
            state = readJsonObject(statePath);
        } catch (Exception ignored) {
            state = new JsonObject();
        }
        state.addProperty("currentSeedVersion", version);
        writeJsonObjectAtomic(statePath, state);
    }

    private static Path seedStatePath(Path gameCoreDir) {
        return gameCoreDir.resolve("A_Potato_Seed").resolve(SEED_STATE_FILE);
    }

    private JsonObject fetchJson(String url, String label) throws Exception {
        return executeWithRetry(label, () -> {
            HttpURLConnection conn = openConnection(url, JSON_CONNECT_TIMEOUT_MS, JSON_READ_TIMEOUT_MS);
            try {
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new DownloadHttpException(responseCode, url);
                }
                try (InputStream inputStream = conn.getInputStream();
                        InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (element == null || !element.isJsonObject()) {
                        throw new IOException("Invalid JSON object from " + url);
                    }
                    return element.getAsJsonObject();
                }
            } finally {
                conn.disconnect();
            }
        });
    }

    private void downloadFile(String url, Path target, String label) throws Exception {
        executeWithRetry(label, () -> {
            Files.createDirectories(target.getParent());
            Files.deleteIfExists(target);
            HttpURLConnection conn = openConnection(url, DOWNLOAD_CONNECT_TIMEOUT_MS, DOWNLOAD_READ_TIMEOUT_MS);
            try {
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new DownloadHttpException(responseCode, url);
                }
                try (InputStream inputStream = conn.getInputStream()) {
                    Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return null;
            } finally {
                conn.disconnect();
            }
        });
    }

    private static HttpURLConnection openConnection(String url, int connectTimeoutMs, int readTimeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(connectTimeoutMs);
        conn.setReadTimeout(readTimeoutMs);
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Accept-Encoding", "identity");
        conn.setRequestProperty("Connection", "close");
        return conn;
    }

    private static void validateSeedJar(Path seedJar) throws Exception {
        if (!Files.isRegularFile(seedJar) || Files.size(seedJar) <= 0L) {
            throw new IOException("Seed jar is missing or empty: " + seedJar);
        }
        try (JarFile jarFile = new JarFile(seedJar.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                throw new IOException("Seed jar manifest is missing: " + seedJar);
            }
            String premainClass = manifest.getMainAttributes().getValue("Premain-Class");
            if (!EXPECTED_PREMAIN_CLASS.equals(premainClass)) {
                throw new IOException("Seed jar Premain-Class is invalid: " + premainClass);
            }
        }
    }

    private static JsonObject readJsonObject(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new JsonObject();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        }
    }

    private static void writePendingMeta(Path pendingDir, CheckResult checkResult) throws IOException {
        JsonObject meta = new JsonObject();
        meta.addProperty("version", checkResult.getRemoteVersion());
        meta.addProperty("Potato_Seed", checkResult.getSeedUrl());
        meta.addProperty("seed_version", checkResult.getSeedVersionUrl());
        writeJsonObjectAtomic(pendingDir.resolve(PENDING_META_FILE), meta);
    }

    private static void writeJsonObjectAtomic(Path path, JsonObject object) throws IOException {
        Files.createDirectories(path.getParent());
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
            GSON.toJson(object, writer);
        }
        moveReplacing(tempPath, path);
    }

    private static void copyWithRetry(Path source, Path target) throws Exception {
        long deadline = System.currentTimeMillis() + APPLY_RETRY_TIMEOUT_MS;
        IOException lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                lastError = e;
                Thread.sleep(APPLY_RETRY_DELAY_MS);
            }
        }
        throw new IOException("Timed out copying Seed jar to " + target, lastError);
    }

    private static void moveWithRetry(Path source, Path target) throws Exception {
        long deadline = System.currentTimeMillis() + APPLY_RETRY_TIMEOUT_MS;
        IOException lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                moveReplacing(source, target);
                return;
            } catch (IOException e) {
                lastError = e;
                Thread.sleep(APPLY_RETRY_DELAY_MS);
            }
        }
        throw new IOException("Timed out replacing Seed jar at " + target, lastError);
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String resolveSibling(String baseUrl, String siblingName) throws Exception {
        return new URI(baseUrl).resolve(siblingName).toString();
    }

    private static String requiredString(JsonObject object, String key, String errorMessage) throws IOException {
        String value = optionalString(object, key);
        if (value.isBlank()) {
            throw new IOException(errorMessage);
        }
        return value;
    }

    private static String optionalString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString().trim();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static <T> T executeWithRetry(String label, RetryableAction<T> action) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.run();
            } catch (Exception e) {
                lastError = e;
                if (attempt >= MAX_ATTEMPTS || !shouldRetry(e)) {
                    throw e;
                }
                System.err.println("[PotatoUpdater] Retry " + attempt + "/" + MAX_ATTEMPTS + " for " + label
                        + ": " + e.getMessage());
                Thread.sleep(RETRY_DELAY_MS);
            }
        }
        throw lastError == null ? new IOException("Unknown Seed self-update failure for " + label) : lastError;
    }

    private static boolean shouldRetry(Exception e) {
        return e instanceof IOException;
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
        }
    }

    private interface RetryableAction<T> {
        T run() throws Exception;
    }

    private static final class DownloadHttpException extends IOException {
        private DownloadHttpException(int statusCode, String url) {
            super("HTTP " + statusCode + " URL: " + url);
        }
    }

    public static final class CheckResult {
        private final boolean updateAvailable;
        private final String localVersion;
        private final String remoteVersion;
        private final String seedUrl;
        private final String seedVersionUrl;

        private CheckResult(boolean updateAvailable,
                            String localVersion,
                            String remoteVersion,
                            String seedUrl,
                            String seedVersionUrl) {
            this.updateAvailable = updateAvailable;
            this.localVersion = localVersion;
            this.remoteVersion = remoteVersion;
            this.seedUrl = seedUrl;
            this.seedVersionUrl = seedVersionUrl;
        }

        public boolean isUpdateAvailable() {
            return updateAvailable;
        }

        public String getLocalVersion() {
            return localVersion;
        }

        public String getRemoteVersion() {
            return remoteVersion;
        }

        public String getSeedUrl() {
            return seedUrl;
        }

        public String getSeedVersionUrl() {
            return seedVersionUrl;
        }
    }

    public static final class PreparedUpdate {
        private final Path pendingDir;
        private final String version;

        private PreparedUpdate(Path pendingDir, String version) {
            this.pendingDir = pendingDir;
            this.version = version;
        }

        public Path getPendingDir() {
            return pendingDir;
        }

        public String getVersion() {
            return version;
        }
    }
}
