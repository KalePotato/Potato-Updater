package com.potato.seed.core;

import com.google.gson.Gson;
import com.potato.seed.config.SeedConfig;
import com.potato.seed.state.SeedStateManager;
import com.potato.seed.ui.SeedGUI;
import com.potato.seed.util.SeedHashUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class UpdaterFetcher {
    private static final int META_CONNECT_TIMEOUT_MS = 8_000;
    private static final int META_READ_TIMEOUT_MS = 8_000;
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 15_000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 30_000;
    private static final int META_MAX_ATTEMPTS = 3;
    private static final int DOWNLOAD_MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1_000L;
    private static final String USER_AGENT =
            "Mozilla/5.0 AppleWebKit/537.36 PotatoSeed/1.0";

    private final SeedConfig config;
    private final Path gameCorePath;
    private final SeedStateManager stateManager;
    private final SeedGUI gui;

    public UpdaterFetcher(SeedConfig config, SeedStateManager stateManager, Path gameCorePath, SeedGUI gui) {
        this.config = config;
        this.stateManager = stateManager;
        this.gameCorePath = gameCorePath;
        this.gui = gui;
    }

    public void checkAndFetch() throws Exception {
        if (!config.isEnableUpdaterCheck()) {
            System.out.println(
                    "[PotatoSeed] Updater check is disabled via config (enableUpdaterCheck=false). Skipping remote check.");
            return;
        }

        System.out.println("[PotatoSeed] Fetching remote seed config from: " + config.getRemoteConfigUrl());
        try {
            if (gui != null) {
                gui.updateStatus("seed check");
            }
            UpdaterMeta remoteMeta = fetchRemoteMeta(config.getRemoteConfigUrl());
            if (remoteMeta == null) {
                throw new Exception("无法获取远端配置文件\n请检查网络连接或稍后重试。");
            }

            if (Boolean.FALSE.equals(remoteMeta.getEnabled())) {
                throw new Exception("维护中心暂时禁用了客户端的更新通道。");
            }

            if (remoteMeta.getVersion() == null || remoteMeta.getVersion().isEmpty()) {
                throw new Exception("远端配置数据异常\n缺少必要的新版本标定字段。");
            }
            if (remoteMeta.getPotatoUpdaterUrl() == null || remoteMeta.getPotatoUpdaterUrl().isEmpty()) {
                throw new Exception("远端配置数据结构缺失\n未找到更新核心的下发路径。");
            }

            String localVersion = stateManager.getState().getCurrentUpdaterVersion();
            String remoteVersion = remoteMeta.getVersion();

            Path updaterDir = gameCorePath.resolve(config.getUpdaterDirName());
            Files.createDirectories(updaterDir);
            Path localUpdaterJar = updaterDir.resolve(config.getUpdaterJarName());

            boolean needsUpdate = false;
            if (!Files.exists(localUpdaterJar)) {
                System.out.println("[PotatoSeed] Local updater jar missing. Needs full download.");
                needsUpdate = true;
            } else if (!remoteVersion.equals(localVersion)) {
                System.out.println("[PotatoSeed] Local version (" + localVersion + ") differs from remote ("
                        + remoteVersion + "). Needs update.");
                needsUpdate = true;
            } else {
                System.out.println("[PotatoSeed] Local updater is already at the latest version (" + localVersion
                        + "). Skip downloading.");
                if (gui != null) {
                    gui.updateStatus("updater ok");
                }
            }

            if (needsUpdate) {
                System.out.println("[PotatoSeed] Downloading new potato_updater.jar...");
                if (gui != null) {
                    gui.updateStatus("fetch updater");
                }
                downloadFileSafely(remoteMeta.getPotatoUpdaterUrl(), remoteMeta.getHash256(), localUpdaterJar);

                stateManager.getState().setCurrentUpdaterVersion(remoteVersion);
                stateManager.save();
                System.out.println("[PotatoSeed] Updater downloaded, verified, and local version record updated to: "
                        + remoteVersion);
            }
        } catch (Exception e) {
            System.err.println("[PotatoSeed] Exception during updater fetch: " + e.getMessage());
            String msg = e.getMessage();
            if (msg == null || !msg.contains("\n")) {
                msg = "更新引导发生故障\n详细信息参阅崩溃日志";
            }
            throw new Exception(msg, e);
        }
    }

    private UpdaterMeta fetchRemoteMeta(final String fetchUrl) throws Exception {
        return executeWithRetry("seed config", META_MAX_ATTEMPTS, new RetryableAction<UpdaterMeta>() {
            @Override
            public UpdaterMeta run() throws Exception {
                HttpURLConnection conn = openConnection(fetchUrl, META_CONNECT_TIMEOUT_MS, META_READ_TIMEOUT_MS);
                try {
                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        try (InputStream is = conn.getInputStream();
                                InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                            return new Gson().fromJson(reader, UpdaterMeta.class);
                        }
                    }
                    throw new IOException("HTTP " + responseCode + " URL: " + fetchUrl);
                } finally {
                    conn.disconnect();
                }
            }
        });
    }

    private void downloadFileSafely(final String fileUrl, final String expectedHash, final Path targetPath) throws Exception {
        final Path tempPath = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
        executeWithRetry("updater jar", DOWNLOAD_MAX_ATTEMPTS, new RetryableAction<Void>() {
            @Override
            public Void run() throws Exception {
                Files.deleteIfExists(tempPath);

                HttpURLConnection conn = openConnection(fileUrl, DOWNLOAD_CONNECT_TIMEOUT_MS, DOWNLOAD_READ_TIMEOUT_MS);
                try {
                    int responseCode = conn.getResponseCode();
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        throw new IOException("HTTP " + responseCode + " URL: " + fileUrl);
                    }

                    try (InputStream in = conn.getInputStream()) {
                        Files.copy(in, tempPath, StandardCopyOption.REPLACE_EXISTING);
                    }

                    if (expectedHash != null && !expectedHash.trim().isEmpty()) {
                        String downloadedHash = SeedHashUtil.calculateSHA256(tempPath);
                        if (!expectedHash.equalsIgnoreCase(downloadedHash)) {
                            throw new IOException("Hash mismatch for " + fileUrl);
                        }
                    }

                    Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    return null;
                } finally {
                    conn.disconnect();
                }
            }

            @Override
            public void onFailure(Exception e) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {
                }
            }
        });
    }

    private HttpURLConnection openConnection(String urlText, int connectTimeoutMs, int readTimeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlText).openConnection();
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

    private <T> T executeWithRetry(String label, int maxAttempts, RetryableAction<T> action) throws Exception {
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.run();
            } catch (Exception e) {
                action.onFailure(e);
                lastError = e;
                if (attempt >= maxAttempts || !shouldRetry(e)) {
                    throw e;
                }
                System.err.println("[PotatoSeed] Retry " + attempt + "/" + maxAttempts + " for " + label
                        + ": " + e.getMessage());
                sleepBeforeRetry();
            }
        }
        throw lastError == null ? new IOException("Unknown failure while fetching " + label) : lastError;
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

    private interface RetryableAction<T> {
        T run() throws Exception;

        default void onFailure(Exception e) {
        }
    }
}
