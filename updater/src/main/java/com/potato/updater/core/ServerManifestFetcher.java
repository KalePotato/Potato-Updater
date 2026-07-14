package com.potato.updater.core;

import com.google.gson.Gson;
import com.potato.updater.model.DeleteZone;
import com.potato.updater.model.ModsControl;
import com.potato.updater.model.ServerManifest;
import com.potato.updater.model.StorageTarget;
import com.potato.updater.model.UpdateInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;

public class ServerManifestFetcher {
    private static final int META_CONNECT_TIMEOUT_MS = 8_000;
    private static final int META_READ_TIMEOUT_MS = 8_000;
    private static final int LOGO_CONNECT_TIMEOUT_MS = 8_000;
    private static final int LOGO_READ_TIMEOUT_MS = 15_000;
    private static final int META_MAX_ATTEMPTS = 3;
    private static final int LOGO_MAX_ATTEMPTS = 2;
    private static final long RETRY_DELAY_MS = 1_000L;
    private static final String USER_AGENT =
            "Mozilla/5.0 AppleWebKit/537.36 PotatoUpdater/1.0";

    private final Gson gson;

    public ServerManifestFetcher(Gson gson) {
        this.gson = gson;
    }

    public ServerManifest fetchManifest(String urlStr) {
        return fetchJsonWithRetry(urlStr, ServerManifest.class, "manifest");
    }

    public StorageTarget fetchStorageTarget(String urlStr) {
        return fetchJsonWithRetry(urlStr, StorageTarget.class, "storage target");
    }

    public DeleteZone fetchDeleteZone(final String urlStr) {
        try {
            return executeWithRetry("delete zone", META_MAX_ATTEMPTS, new Callable<DeleteZone>() {
                @Override
                public DeleteZone call() throws Exception {
                    HttpURLConnection conn = openJsonConnection(urlStr);
                    try {
                        int responseCode = conn.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            try (InputStream is = conn.getInputStream();
                                    InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                                return gson.fromJson(reader, DeleteZone.class);
                            }
                        }
                        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                            return new DeleteZone();
                        }
                        throw new IOException("HTTP " + responseCode + " URL: " + urlStr);
                    } finally {
                        conn.disconnect();
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("[PotatoUpdater] Failed to fetch delete zone: " + e.getMessage());
            return null;
        }
    }

    public UpdateInfo fetchUpdateInfo(final String urlStr) {
        try {
            return executeWithRetry("update info", 2, new Callable<UpdateInfo>() {
                @Override
                public UpdateInfo call() throws Exception {
                    HttpURLConnection conn = openJsonConnection(urlStr);
                    try {
                        int responseCode = conn.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(),
                                    StandardCharsets.UTF_8)) {
                                return gson.fromJson(reader, UpdateInfo.class);
                            }
                        }
                        throw new IOException("HTTP " + responseCode + " URL: " + urlStr);
                    } finally {
                        conn.disconnect();
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("[PotatoUpdater] Failed to fetch update info: " + e.getMessage());
            return null;
        }
    }

    public Set<String> fetchOptionalResourcePacks(final String urlStr) {
        try {
            return executeWithRetry("optional resource packs", 1, new Callable<Set<String>>() {
                @Override
                public Set<String> call() throws Exception {
                    HttpURLConnection conn = openJsonConnection(urlStr);
                    try {
                        int responseCode = conn.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(),
                                    StandardCharsets.UTF_8)) {
                                String[] fileNames = gson.fromJson(reader, String[].class);
                                Set<String> result = new LinkedHashSet<>();
                                if (fileNames != null) {
                                    Arrays.stream(fileNames)
                                            .filter(name -> name != null && !name.isBlank())
                                            .map(String::trim)
                                            .forEach(result::add);
                                }
                                return result;
                            }
                        }
                        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                            return new LinkedHashSet<>();
                        }
                        throw new IOException("HTTP " + responseCode + " URL: " + urlStr);
                    } finally {
                        conn.disconnect();
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("[PotatoUpdater] Optional resource pack list unavailable: " + e.getMessage());
            return new LinkedHashSet<>();
        }
    }

    public ModsControl fetchModsControl(final String urlStr) {
        try {
            return executeWithRetry("mods control", 1, new Callable<ModsControl>() {
                @Override
                public ModsControl call() throws Exception {
                    HttpURLConnection conn = openJsonConnection(urlStr);
                    try {
                        int responseCode = conn.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(),
                                    StandardCharsets.UTF_8)) {
                                ModsControl control = gson.fromJson(reader, ModsControl.class);
                                return control != null ? control : new ModsControl();
                            }
                        }
                        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                            return new ModsControl();
                        }
                        throw new IOException("HTTP " + responseCode + " URL: " + urlStr);
                    } finally {
                        conn.disconnect();
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("[PotatoUpdater] Mods control unavailable: " + e.getMessage());
            return new ModsControl();
        }
    }

    public Path downloadLogo(final String urlStr, Path tempDir) {
        return downloadLogo(urlStr, tempDir, "logo_cache.png");
    }

    public Path downloadLogo(final String urlStr, Path tempDir, String cacheFileName) {
        final Path target = tempDir.resolve(cacheFileName);
        final Path tempTarget = tempDir.resolve(cacheFileName + ".tmp");
        try {
            Files.createDirectories(tempDir);
            return executeWithRetry("logo", LOGO_MAX_ATTEMPTS, new Callable<Path>() {
                @Override
                public Path call() throws Exception {
                    HttpURLConnection conn = openLogoConnection(urlStr);
                    try {
                        int responseCode = conn.getResponseCode();
                        if (responseCode != HttpURLConnection.HTTP_OK) {
                            throw new IOException("HTTP " + responseCode + " URL: " + urlStr);
                        }
                        try (InputStream in = conn.getInputStream()) {
                            Files.copy(in, tempTarget, StandardCopyOption.REPLACE_EXISTING);
                        }
                        Files.move(tempTarget, target, StandardCopyOption.REPLACE_EXISTING);
                        return target;
                    } finally {
                        conn.disconnect();
                    }
                }
            });
        } catch (Exception e) {
            try {
                Files.deleteIfExists(tempTarget);
            } catch (IOException ignored) {
            }
            System.err.println("[PotatoUpdater] Failed to download logo: " + e.getMessage());
            return null;
        }
    }

    private <T> T fetchJsonWithRetry(final String urlStr, final Class<T> type, final String label) {
        try {
            return executeWithRetry(label, META_MAX_ATTEMPTS, new Callable<T>() {
                @Override
                public T call() throws Exception {
                    HttpURLConnection conn = openJsonConnection(urlStr);
                    try {
                        int responseCode = conn.getResponseCode();
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            try (InputStream is = conn.getInputStream();
                                    InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                                return gson.fromJson(reader, type);
                            }
                        }
                        throw new IOException("HTTP " + responseCode + " URL: " + urlStr);
                    } finally {
                        conn.disconnect();
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("[PotatoUpdater] Failed to fetch " + label + ": " + e.getMessage());
            return null;
        }
    }

    private HttpURLConnection openJsonConnection(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(META_CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(META_READ_TIMEOUT_MS);
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/json,text/plain,*/*");
        conn.setRequestProperty("Accept-Encoding", "identity");
        conn.setRequestProperty("Connection", "close");
        return conn;
    }

    private HttpURLConnection openLogoConnection(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(LOGO_CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(LOGO_READ_TIMEOUT_MS);
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "image/*,*/*");
        conn.setRequestProperty("Accept-Encoding", "identity");
        conn.setRequestProperty("Connection", "close");
        return conn;
    }

    private <T> T executeWithRetry(String label, int maxAttempts, Callable<T> action) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.call();
            } catch (Exception e) {
                lastError = e;
                if (attempt >= maxAttempts || !shouldRetry(e)) {
                    throw e;
                }
                System.err.println("[PotatoUpdater] Retry " + attempt + "/" + maxAttempts + " for " + label
                        + ": " + e.getMessage());
                sleepBeforeRetry();
            }
        }
        throw lastError == null ? new IOException("Unknown fetch failure for " + label) : lastError;
    }

    private boolean shouldRetry(Exception e) {
        if (!(e instanceof IOException)) {
            return false;
        }
        String message = e.getMessage();
        if (message != null && message.startsWith("HTTP ")) {
            int statusCode = parseStatusCode(message);
            return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500;
        }
        return true;
    }

    private int parseStatusCode(String message) {
        try {
            int start = "HTTP ".length();
            int end = message.indexOf(' ', start);
            String codeText = end > start ? message.substring(start, end) : message.substring(start).trim();
            return Integer.parseInt(codeText.trim());
        } catch (Exception ignored) {
            return -1;
        }
    }

    private void sleepBeforeRetry() throws InterruptedException {
        Thread.sleep(RETRY_DELAY_MS);
    }
}
