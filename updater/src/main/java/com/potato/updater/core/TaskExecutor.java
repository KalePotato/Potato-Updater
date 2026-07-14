package com.potato.updater.core;

import com.potato.updater.model.DeleteZone;
import com.potato.updater.model.FileEntry;
import com.potato.updater.util.HashUtil;
import com.potato.updater.util.PathResolver;
import com.potato.updater.util.UpdaterErrorLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileSystemException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class TaskExecutor {

    private static final long APPLY_TIMEOUT_MS = 45_000L;
    private static final long APPLY_RETRY_DELAY_MS = 250L;
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 15_000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 12_000;
    private static final int DOWNLOAD_HEAD_TIMEOUT_MS = 5_000;
    private static final int DOWNLOAD_MAX_RETRIES = 3;
    private static final long DOWNLOAD_STALL_TIMEOUT_MS = 30_000L;
    private static final long DOWNLOAD_RETRY_DELAY_MS = 1_200L;
    private static final long DISK_SPACE_HEADROOM_BYTES = 128L * 1024L * 1024L;
    private static final long DISK_SPACE_CHECK_INTERVAL_BYTES = 8L * 1024L * 1024L;
    private static final long DOWNLOAD_SPEED_IDLE_RESET_NANOS = 900_000_000L;
    private static final long DOWNLOAD_STATUS_REPORT_NANOS = 120_000_000L;
    private static final int DOWNLOAD_WORKER_MAX = 6;
    private static final int DOWNLOAD_REMOTE_CONNECTION_MAX = 3;
    private static final boolean VERBOSE_TRANSFER_TRACE = Boolean.getBoolean("potato.updater.verboseTransfers");
    private static final int SIZE_PROBE_WORKER_MAX = 8;
    private static final boolean ENABLE_RUNTIME_SIZE_PROBE = false;
    private static final String SESSION_DIR_PREFIX = "session_";
    private static final String CLEANUP_DIR_PREFIX = "temp_downloads_cleanup_";
    private static final String MODS_STAGE_PREFIX = "mods_stage_";
    private static final String MODS_BACKUP_PREFIX = "mods_backup_";
    private static final String MODS_PENDING_PREFIX = "mods_pending_";
    private static final String MODS_PAYLOAD_DIR = "payload";
    private static final String MODS_DELETE_LIST_FILE = "delete_paths.txt";

    public static final class UpdateResult {
        public enum Status {
            SUCCESS,
            FAILED,
            RESTART_REQUIRED
        }

        private final Status status;
        private final Path pendingModsDir;

        private UpdateResult(Status status, Path pendingModsDir) {
            this.status = status;
            this.pendingModsDir = pendingModsDir;
        }

        public static UpdateResult success() {
            return new UpdateResult(Status.SUCCESS, null);
        }

        public static UpdateResult failed() {
            return new UpdateResult(Status.FAILED, null);
        }

        public static UpdateResult restartRequired(Path pendingModsDir) {
            return new UpdateResult(Status.RESTART_REQUIRED, pendingModsDir);
        }

        public Status getStatus() {
            return status;
        }

        public Path getPendingModsDir() {
            return pendingModsDir;
        }
    }

    private static final class DownloadHttpException extends IOException {
        private final int statusCode;

        private DownloadHttpException(int statusCode, String url) {
            super("HTTP " + statusCode + " URL: " + url);
            this.statusCode = statusCode;
        }

        private int getStatusCode() {
            return statusCode;
        }
    }

    private final PathResolver pathResolver;
    private final Path tempDownloadsRoot;
    private final Path tempDownloadsDir;
    private final String targetBaseUrl;
    private final Path localMirrorRoot;
    private final Path localPackZip;
    private final ExecutorService networkTransferExecutor;
    private final Semaphore remoteDownloadPermits;
    private final Map<String, Path> localMirrorHashIndex = new HashMap<>();
    private final Map<String, Path> localMirrorFileNameIndex = new HashMap<>();
    private final Map<String, Path> localMirrorDirectoryNameIndex = new HashMap<>();
    private final Map<FileEntry, Path> preparedFiles = new ConcurrentHashMap<>();
    private final Map<FileEntry, Long> activeTransferBytes = new ConcurrentHashMap<>();
    private final Map<FileEntry, Long> activeTransferStartedAt = new ConcurrentHashMap<>();
    private final Consumer<String> statusConsumer;
    private final String sessionToken = Long.toHexString(System.nanoTime());
    private final AtomicInteger completedFiles = new AtomicInteger();
    private final AtomicLong transferredBytes = new AtomicLong();
    private final Object transferStatsLock = new Object();
    private final Object plannedBytesLock = new Object();
    private final Map<FileEntry, Long> resolvedTransferSizes = new ConcurrentHashMap<>();
    private final Path staleCleanupDir;
    private final boolean directModsApply;
    private volatile int totalFiles = 0;
    private volatile long plannedTransferBytes = 0L;
    private volatile long transferStartedAtNanos = System.nanoTime();
    private volatile long speedSampleStartedAtNanos = 0L;
    private volatile long speedSampleStartedBytes = 0L;
    private volatile long lastByteProgressAtNanos = 0L;
    private volatile long lastMeasuredSpeedBytesPerSecond = 0L;
    private volatile String lastPublishedStatus = "";
    private volatile String currentTransferLabel = "";

    private static final class DownloadStatusSnapshot {
        private final int completedFiles;
        private final int totalFiles;
        private final long visibleTransferredBytes;
        private final long plannedTransferBytes;
        private final int resolvedTransferSizes;
        private final long bytesPerSecond;
        private final String transferLabel;

        private DownloadStatusSnapshot(int completedFiles,
                                       int totalFiles,
                                       long visibleTransferredBytes,
                                       long plannedTransferBytes,
                                       int resolvedTransferSizes,
                                       long bytesPerSecond,
                                       String transferLabel) {
            this.completedFiles = completedFiles;
            this.totalFiles = totalFiles;
            this.visibleTransferredBytes = visibleTransferredBytes;
            this.plannedTransferBytes = plannedTransferBytes;
            this.resolvedTransferSizes = resolvedTransferSizes;
            this.bytesPerSecond = bytesPerSecond;
            this.transferLabel = transferLabel;
        }
    }

    public TaskExecutor(PathResolver pathResolver, String targetBaseUrl) {
        this(pathResolver, targetBaseUrl, null, false);
    }

    public TaskExecutor(PathResolver pathResolver, String targetBaseUrl, Consumer<String> statusConsumer) {
        this(pathResolver, targetBaseUrl, statusConsumer, false);
    }

    public TaskExecutor(PathResolver pathResolver,
                        String targetBaseUrl,
                        Consumer<String> statusConsumer,
                        boolean directModsApply) {
        this.pathResolver = pathResolver;
        this.targetBaseUrl = targetBaseUrl;
        this.statusConsumer = statusConsumer;
        this.directModsApply = directModsApply;
        this.tempDownloadsRoot = pathResolver.getGameCoreDirectory()
                .resolve("A_Potato_Updater")
                .resolve("temp_downloads");
        this.tempDownloadsDir = tempDownloadsRoot.resolve(SESSION_DIR_PREFIX + sessionToken);
        this.staleCleanupDir = tempDownloadsRoot.resolveSibling("temp_downloads_cleanup_" + sessionToken);
        this.localMirrorRoot = findLocalMirrorRoot(pathResolver.getGameCoreDirectory());
        this.localPackZip = findLocalPackZip(pathResolver.getGameCoreDirectory());
        this.remoteDownloadPermits = new Semaphore(DOWNLOAD_REMOTE_CONNECTION_MAX, true);
        this.networkTransferExecutor = Executors.newFixedThreadPool(DOWNLOAD_WORKER_MAX, r -> {
            Thread thread = new Thread(r, "potato-download-net");
            thread.setDaemon(true);
            return thread;
        });
        buildLocalMirrorIndexes();

        try {
            prepareTempWorkspace();
            Files.createDirectories(this.tempDownloadsDir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public UpdateResult executeUpdate(DiffEngine.DiffResult diff) {
        System.out.println("============== Task Start ==============");

        try {
            downloadAllToTemp(diff.toDownloadOrUpdate);
            System.out.println("All files prepared. Start apply.");

            List<FileEntry> regularEntries = new ArrayList<>();
            List<FileEntry> modEntries = new ArrayList<>();
            splitEntries(diff.toDownloadOrUpdate, regularEntries, modEntries);

            List<DeleteZone.DeleteItem> regularDeletes = new ArrayList<>();
            List<DeleteZone.DeleteItem> modDeletes = new ArrayList<>();
            splitDeletes(diff.toDelete, regularDeletes, modDeletes);

            int applyIndex = 0;
            for (FileEntry entry : regularEntries) {
                applyIndex++;
                publishStatus("apply " + applyIndex + "/" + totalFiles + " " + compactName(entry));
                Path sourceTemp = preparedFiles.remove(entry);
                if (sourceTemp == null) {
                    throw new IOException("Missing prepared temp file for " + entry.getPath());
                }

                applyPreparedFileWithTimeout(entry, sourceTemp);
            }

            for (DeleteZone.DeleteItem delItem : regularDeletes) {
                Path targetReal = pathResolver.resolvePath(delItem.getPath());
                if (Files.deleteIfExists(targetReal)) {
                    System.out.println("  [Delete] -> " + targetReal.toAbsolutePath());
                }
            }

            if (!modEntries.isEmpty() || !modDeletes.isEmpty()) {
                Path pendingModsDir = stagePendingModsTransaction(modEntries, modDeletes, applyIndex);
                if (directModsApply) {
                    applyPendingModsTransaction(pathResolver.getGameCoreDirectory(), pendingModsDir,
                            this::publishStatus, applyIndex, totalFiles);
                } else {
                    publishStatus("finalize");
                    System.out.println("============== Task Staged ==============");
                    return UpdateResult.restartRequired(pendingModsDir);
                }
            }

            publishStatus("finalize");
            System.out.println("============== Task Done ==============");
            return UpdateResult.success();
        } catch (Exception e) {
            System.err.println("Task failed.");
            UpdaterErrorLogger.logError("Fatal exception during download and replace file operations", e);
            e.printStackTrace();
            return UpdateResult.failed();
        } finally {
            clearTempDir();
            preparedFiles.clear();
            networkTransferExecutor.shutdownNow();
        }
    }

    private void downloadAllToTemp(List<FileEntry> entries) throws Exception {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        totalFiles = entries.size();
        completedFiles.set(0);
        transferredBytes.set(0L);
        activeTransferBytes.clear();
        resetTransferStats();
        plannedTransferBytes = 0L;
        resolvedTransferSizes.clear();
        resolvePlannedTransferBytes(entries);
        publishStatus(buildDownloadStatus());

        int poolSize = Math.max(4, Math.min(DOWNLOAD_WORKER_MAX, entries.size()));
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (FileEntry entry : entries) {
            tasks.add(() -> {
                prepareFile(entry);
                return null;
            });
        }

        try {
            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new Exception("Download stage failed", cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private void prepareFile(FileEntry entry) throws Exception {
        Path tempFile = createTempFilePath(entry);
        preparedFiles.put(entry, tempFile);

        try {
            Path localMirrorFile = resolveLocalMirrorFile(entry, false);
            if (localMirrorFile != null && Files.exists(localMirrorFile) && !Files.isDirectory(localMirrorFile)) {
                if (tryMaterializeAndVerify(entry, localMirrorFile, tempFile, false)) {
                    markPrepared();
                    return;
                }
            }

            String localPackHash = materializeFromLocalPack(entry, tempFile);
            if (localPackHash != null) {
                if (tryVerifyPreparedFile(entry, tempFile, localPackHash)) {
                    markPrepared();
                    return;
                }
            }

            if (localMirrorFile != null && Files.exists(localMirrorFile)) {
                if (tryMaterializeAndVerify(entry, localMirrorFile, tempFile, true)) {
                    markPrepared();
                    return;
                }
            }

            downloadToTempAndVerify(entry, tempFile);
            markPrepared();
        } catch (Exception e) {
            cancelActiveTransfer(entry);
            preparedFiles.remove(entry);
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
            throw e;
        }
    }

    private void applyPreparedFileWithTimeout(FileEntry entry, Path sourceTemp) throws Exception {
        Path targetReal = pathResolver.resolvePath(entry.getPath());
        Files.createDirectories(targetReal.getParent());

        long deadline = System.currentTimeMillis() + APPLY_TIMEOUT_MS;
        IOException lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                Files.move(sourceTemp, targetReal, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("  [Apply] -> " + targetReal.toAbsolutePath());
                return;
            } catch (IOException e) {
                lastError = e;
                if (!isTransientApplyError(e)) {
                    throw e;
                }

                try {
                    Thread.sleep(APPLY_RETRY_DELAY_MS);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Apply interrupted for " + entry.getPath(), interruptedException);
                }
            }
        }

        throw new IOException("Apply timed out for " + entry.getPath(), lastError);
    }

    private Path createTempFilePath(FileEntry entry) throws IOException {
        String pathKey = entry.getPath() == null ? "unknown" : Integer.toHexString(entry.getPath().hashCode());
        String hashKey = entry.getHash256() == null ? "nohash" : entry.getHash256().substring(0, Math.min(8, entry.getHash256().length()));
        String prefix = sessionToken + "-" + pathKey + "-" + hashKey + "-";
        if (prefix.length() < 3) {
            prefix = "upd-";
        }
        return Files.createTempFile(tempDownloadsDir, prefix, ".tmp");
    }

    private void downloadToTempAndVerify(FileEntry entry, Path tempFile) throws Exception {
        String downloadUrl = resolveDownloadUrl(entry);
        currentTransferLabel = compactName(entry);
        if (VERBOSE_TRANSFER_TRACE) {
            System.out.println("  [Fetch] " + downloadUrl);
        }

        Exception lastError = null;
        for (int attempt = 1; attempt <= DOWNLOAD_MAX_RETRIES; attempt++) {
            try {
                Files.deleteIfExists(tempFile);
                beginActiveTransfer(entry);
                runRemoteDownloadAttempt(entry, tempFile, downloadUrl);
                commitActiveTransfer(entry);
                currentTransferLabel = "";
                return;
            } catch (Exception e) {
                lastError = e;
                cancelActiveTransfer(entry);
                Files.deleteIfExists(tempFile);
                Integer httpStatusCode = extractHttpStatusCode(e);
                if (httpStatusCode != null && httpStatusCode >= 400 && httpStatusCode < 500) {
                    publishStatus("download missing " + compactName(entry) + " (" + httpStatusCode + ")");
                } else if (isDiskSpaceFailure(e)) {
                    publishStatus("disk full " + compactName(entry));
                }
                if (attempt >= DOWNLOAD_MAX_RETRIES || !shouldRetryDownload(e)) {
                    throw e;
                }
                if (VERBOSE_TRANSFER_TRACE) {
                    System.out.println("  [Fetch Retry " + attempt + "/" + DOWNLOAD_MAX_RETRIES + "] " + compactName(entry));
                }
                sleepBeforeRetry();
            }
        }

        if (lastError != null) {
            currentTransferLabel = "";
            throw lastError;
        }
    }

    private void runRemoteDownloadAttempt(FileEntry entry, Path tempFile, String downloadUrl) throws Exception {
        Future<Void> transferFuture = networkTransferExecutor.submit(() -> {
            boolean permitAcquired = false;
            HttpURLConnection conn = null;
            try {
                remoteDownloadPermits.acquire();
                permitAcquired = true;
                conn = openDownloadConnection(downloadUrl);
                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new DownloadHttpException(responseCode, downloadUrl);
                }

                long totalBytes = conn.getContentLengthLong();
                registerResolvedTransferBytes(entry, totalBytes);
                try (InputStream in = conn.getInputStream()) {
                    String calculatedHash = copyStreamToFile(entry, in, tempFile, totalBytes);
                    verifyPreparedHash(entry, tempFile, calculatedHash);
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IOException("Download interrupted for " + compactName(entry), interruptedException);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
                if (permitAcquired) {
                    remoteDownloadPermits.release();
                }
            }
            return null;
        });

        long lastObservedBytes = 0L;
        long lastProgressAt = System.currentTimeMillis();
        while (true) {
            try {
                transferFuture.get(500L, TimeUnit.MILLISECONDS);
                return;
            } catch (TimeoutException ignored) {
                long currentBytes = activeTransferBytes.getOrDefault(entry, 0L);
                long now = System.currentTimeMillis();
                if (currentBytes > lastObservedBytes) {
                    lastObservedBytes = currentBytes;
                    lastProgressAt = now;
                } else if (now - lastProgressAt > DOWNLOAD_STALL_TIMEOUT_MS) {
                    transferFuture.cancel(true);
                    throw new IOException("Download stalled for " + compactName(entry));
                }
            } catch (InterruptedException interruptedException) {
                transferFuture.cancel(true);
                Thread.currentThread().interrupt();
                throw new IOException("Download interrupted for " + compactName(entry), interruptedException);
            } catch (java.util.concurrent.ExecutionException executionException) {
                Throwable cause = executionException.getCause();
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                throw new IOException("Download failed for " + compactName(entry), cause);
            }
        }
    }

    private String copyStreamToFile(FileEntry entry, InputStream in, Path tempFile, long totalBytes) throws Exception {
        byte[] buffer = new byte[262144];
        long lastReportAt = 0L;
        long copiedBytes = 0L;
        long bytesSinceSpaceCheck = 0L;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        registerResolvedTransferBytes(entry, totalBytes);
        ensureTempWritableSpace(entry, totalBytes);
        try (OutputStream out = Files.newOutputStream(tempFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                bytesSinceSpaceCheck += bytesRead;
                if (bytesSinceSpaceCheck >= DISK_SPACE_CHECK_INTERVAL_BYTES) {
                    long remainingBytes = totalBytes > 0L ? Math.max(0L, totalBytes - copiedBytes) : 0L;
                    ensureTempWritableSpace(entry, remainingBytes);
                    bytesSinceSpaceCheck = 0L;
                }
                out.write(buffer, 0, bytesRead);
                digest.update(buffer, 0, bytesRead);
                copiedBytes += bytesRead;
                updateActiveTransfer(entry, copiedBytes);

                long now = System.nanoTime();
                if (lastReportAt == 0L || now - lastReportAt >= DOWNLOAD_STATUS_REPORT_NANOS || totalBytes <= 0L) {
                    publishStatus(buildDownloadStatus());
                    lastReportAt = now;
                }
            }
        }
        return bytesToHex(digest.digest());
    }

    private void verifyHash(FileEntry entry, Path file) throws Exception {
        verifyPreparedHash(entry, file, null);
    }

    private void verifyPreparedHash(FileEntry entry, Path file, String calculatedHash) throws Exception {
        String expectedHash = entry.getHash256();
        if (expectedHash == null || expectedHash.isEmpty()) {
            return;
        }

        String effectiveHash = calculatedHash != null ? calculatedHash : HashUtil.calculateSHA256(file);
        if (!expectedHash.equalsIgnoreCase(effectiveHash)) {
            Files.deleteIfExists(file);
            throw new SecurityException("Hash mismatch for " + entry.getPath());
        }
    }

    private boolean tryMaterializeAndVerify(FileEntry entry, Path source, Path tempFile, boolean allowHashFallback) throws Exception {
        try {
            beginActiveTransfer(entry);
            String calculatedHash = materializeLocalMirror(entry, source, tempFile);
            verifyPreparedHash(entry, tempFile, calculatedHash);
            commitActiveTransfer(entry);
            return true;
        } catch (SecurityException ignored) {
            cancelActiveTransfer(entry);
            Files.deleteIfExists(tempFile);
            if (!allowHashFallback) {
                return false;
            }
            return false;
        } catch (Exception e) {
            cancelActiveTransfer(entry);
            throw e;
        }
    }

    private boolean tryVerifyPreparedFile(FileEntry entry, Path tempFile, String calculatedHash) throws Exception {
        try {
            verifyPreparedHash(entry, tempFile, calculatedHash);
            commitActiveTransfer(entry);
            return true;
        } catch (SecurityException ignored) {
            cancelActiveTransfer(entry);
            Files.deleteIfExists(tempFile);
            return false;
        } catch (Exception e) {
            cancelActiveTransfer(entry);
            throw e;
        }
    }

    private void resolvePlannedTransferBytes(List<FileEntry> entries) throws Exception {
        int poolSize = Math.max(2, Math.min(SIZE_PROBE_WORKER_MAX, entries.size()));
        ExecutorService probeExecutor = Executors.newFixedThreadPool(poolSize);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (FileEntry entry : entries) {
            tasks.add(() -> {
                long totalBytes = estimateEntryBytes(entry);
                if (totalBytes < 0L) {
                    totalBytes = probeRemoteContentLength(entry);
                }
                registerResolvedTransferBytes(entry, totalBytes);
                return null;
            });
        }

        try {
            List<Future<Void>> futures = probeExecutor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }
        } finally {
            probeExecutor.shutdownNow();
        }
    }

    private ExecutorService startAsyncSizeProbe(List<FileEntry> entries) {
        if (!ENABLE_RUNTIME_SIZE_PROBE) {
            return null;
        }

        List<FileEntry> unresolved = new ArrayList<>();
        for (FileEntry entry : entries) {
            if (!resolvedTransferSizes.containsKey(entry)) {
                unresolved.add(entry);
            }
        }
        if (unresolved.isEmpty()) {
            return null;
        }

        int poolSize = Math.max(2, Math.min(SIZE_PROBE_WORKER_MAX, unresolved.size()));
        ExecutorService probeExecutor = Executors.newFixedThreadPool(poolSize);
        for (FileEntry entry : unresolved) {
            probeExecutor.submit(() -> probeRemoteTransferBytes(entry));
        }
        return probeExecutor;
    }

    private void probeRemoteTransferBytes(FileEntry entry) {
        long totalBytes = probeRemoteContentLength(entry);
        if (totalBytes > 0L) {
            registerResolvedTransferBytes(entry, totalBytes);
            publishStatus(buildDownloadStatus());
        }
    }

    private long estimateEntryBytes(FileEntry entry) {
        try {
            if (entry != null && entry.hasSizeBytes()) {
                return entry.getSizeBytes();
            }

            Path localMirrorFile = resolveLocalMirrorFile(entry, false);
            if (localMirrorFile != null && Files.exists(localMirrorFile) && Files.isRegularFile(localMirrorFile)) {
                return Files.size(localMirrorFile);
            }
            if (localMirrorFile != null && Files.isDirectory(localMirrorFile)) {
                return calculateDirectoryContentBytes(localMirrorFile);
            }

            long localPackSize = resolveLocalPackEntrySize(entry);
            if (localPackSize > 0L) {
                return localPackSize;
            }
        } catch (Exception e) {
            return -1L;
        }
        return -1L;
    }

    private long calculateDirectoryContentBytes(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return 0L;
        }
        try (Stream<Path> walk = Files.walk(directory)) {
            long total = 0L;
            for (Path path : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                total += Files.size(path);
            }
            return total;
        } catch (Exception e) {
            return 0L;
        }
    }

    private long probeRemoteContentLength(FileEntry entry) {
        String downloadUrl = resolveDownloadUrl(entry);
        long probed = tryProbeContentLength(downloadUrl, true);
        if (probed >= 0L) {
            return probed;
        }
        return tryProbeContentLength(downloadUrl, false);
    }

    private long tryProbeContentLength(String downloadUrl, boolean headRequest) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
            configureDownloadConnection(conn, headRequest);
            if (!headRequest) {
                conn.setRequestProperty("Range", "bytes=0-0");
            }
            int responseCode = conn.getResponseCode();
            if (responseCode >= 400) {
                return -1L;
            }

            long contentLength = conn.getContentLengthLong();
            if (contentLength >= 0L) {
                return contentLength;
            }

            String contentRange = conn.getHeaderField("Content-Range");
            if (contentRange != null) {
                int slashIndex = contentRange.lastIndexOf('/');
                if (slashIndex >= 0 && slashIndex + 1 < contentRange.length()) {
                    try {
                        return Long.parseLong(contentRange.substring(slashIndex + 1).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
            return -1L;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return -1L;
    }

    private long resolveLocalPackEntrySize(FileEntry entry) {
        if (localPackZip == null || !Files.exists(localPackZip)) {
            return 0L;
        }

        String relative = entry.getPath();
        if (relative == null || relative.isEmpty()) {
            return 0L;
        }

        String normalized = relative.replace('\\', '/');
        String[] candidates = new String[] { "Potato_Pack/" + normalized, normalized };
        try (ZipFile zipFile = new ZipFile(localPackZip.toFile())) {
            for (String candidate : candidates) {
                ZipEntry zipEntry = zipFile.getEntry(candidate);
                if (zipEntry != null) {
                    return Math.max(0L, zipEntry.getSize());
                }
            }
        } catch (Exception ignored) {
        }
        return 0L;
    }

    private void clearTempDir() {
        try {
            if (!Files.exists(tempDownloadsDir)) {
                return;
            }
            try (Stream<Path> walk = Files.walk(tempDownloadsDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                        });
            }
            removeEmptyParent(tempDownloadsRoot);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void prepareTempWorkspace() throws IOException {
        Files.createDirectories(tempDownloadsRoot.getParent());
        cleanupTempArtifacts(pathResolver.getGameCoreDirectory());
        Files.createDirectories(tempDownloadsRoot);
    }

    private boolean directoryHasEntries(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.findAny().isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    private void scheduleRecursiveDelete(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Thread cleanupThread = new Thread(() -> {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            } catch (IOException ignored) {
            }
        }, "potato-temp-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    private void removeEmptyParent(Path dir) {
        try {
            if (dir != null && Files.isDirectory(dir)) {
                try (Stream<Path> stream = Files.list(dir)) {
                    if (stream.findAny().isEmpty()) {
                        Files.deleteIfExists(dir);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private boolean isTransientApplyError(IOException e) {
        if (e instanceof java.nio.file.AccessDeniedException) {
            return true;
        }
        if (e instanceof FileSystemException fileSystemException) {
            String reason = fileSystemException.getReason();
            if (reason == null) {
                return true;
            }
            String normalized = reason.toLowerCase();
            return normalized.contains("being used")
                    || normalized.contains("occupied")
                    || normalized.contains("access")
                    || normalized.contains("another process");
        }
        return false;
    }

    private String resolveDownloadUrl(FileEntry entry) {
        String path = entry.getPath();
        if (path != null && (path.startsWith("http://") || path.startsWith("https://"))) {
            return path;
        }

        if (path != null && !path.isEmpty()) {
            String sanitized = path.replace('\\', '/');
            String relativePath = sanitized.startsWith("/") ? sanitized.substring(1) : sanitized;
            String base = targetBaseUrl.endsWith("/") ? targetBaseUrl : targetBaseUrl + "/";
            return joinAndEncodeUrl(base, relativePath);
        }
        return targetBaseUrl;
    }

    private String joinAndEncodeUrl(String baseUrl, String relativePath) {
        String[] segments = relativePath.split("/");
        StringBuilder encodedPath = new StringBuilder();
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (encodedPath.length() > 0) {
                encodedPath.append('/');
            }
            encodedPath.append(encodePathSegment(segment));
        }
        return baseUrl + encodedPath;
    }

    private String encodePathSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private Path resolveLocalMirrorFile(FileEntry entry, boolean allowHashLookup) {
        if (localMirrorRoot == null) {
            return null;
        }

        String relative = entry.getPath();
        if (relative == null || relative.isEmpty()) {
            return null;
        }

        Path byPath = resolveMirrorByPath(relative);
        if (byPath != null && Files.exists(byPath)) {
            return byPath;
        }

        Path byDirectoryName = resolveMirrorByDirectoryName(entry.getFileName());
        if (byDirectoryName != null) {
            return byDirectoryName;
        }

        Path byName = resolveMirrorByFileName(entry.getFileName());
        if (byName != null) {
            return byName;
        }

        return allowHashLookup ? resolveMirrorByHash(entry.getHash256()) : null;
    }

    private void buildLocalMirrorIndexes() {
        if (localMirrorRoot == null || !Files.isDirectory(localMirrorRoot)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(localMirrorRoot)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                Path fileName = path.getFileName();
                if (fileName == null) {
                    continue;
                }
                String key = fileName.toString();
                if (Files.isDirectory(path)) {
                    localMirrorDirectoryNameIndex.putIfAbsent(key, path);
                } else if (Files.isRegularFile(path)) {
                    localMirrorFileNameIndex.putIfAbsent(key, path);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private Path findLocalMirrorRoot(Path start) {
        Path current = start;
        while (current != null) {
            Path remoteSnapshotCandidate = current.resolve("remote").resolve("Potato_Pack");
            if (Files.isDirectory(remoteSnapshotCandidate)) {
                return remoteSnapshotCandidate;
            }

            Path workspaceMirrorCandidate = current.resolve("config_files").resolve("REMOTE_STORAGE_DIR").resolve("Potato_Pack");
            if (Files.isDirectory(workspaceMirrorCandidate)) {
                return workspaceMirrorCandidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private Path findLocalPackZip(Path start) {
        Path current = start;
        while (current != null) {
            Path resultDir = current.resolve("config_files").resolve("Potato_Oven").resolve("RESULT");
            if (Files.exists(resultDir)) {
                try (Stream<Path> walk = Files.list(resultDir)) {
                    return walk.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".zip"))
                            .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                            .orElse(null);
                } catch (Exception ignored) {
                }
            }
            current = current.getParent();
        }
        return null;
    }

    private Path resolveMirrorByPath(String relative) {
        try {
            String normalized = relative.replace('\\', '/');
            String[] segments = normalized.split("/");
            Path candidate = localMirrorRoot;
            for (String segment : segments) {
                if (!segment.isEmpty()) {
                    candidate = candidate.resolve(segment);
                }
            }
            return candidate;
        } catch (Exception e) {
            return null;
        }
    }

    private Path resolveMirrorByFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        return localMirrorFileNameIndex.get(fileName);
    }

    private Path resolveMirrorByDirectoryName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        String baseName = fileName;
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            baseName = fileName.substring(0, dot);
        }
        return localMirrorDirectoryNameIndex.get(baseName);
    }

    private Path resolveMirrorByHash(String hash) {
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        Path cached = localMirrorHashIndex.get(hash.toLowerCase());
        if (cached != null && Files.exists(cached)) {
            return cached;
        }

        try (Stream<Path> walk = Files.walk(localMirrorRoot)) {
            for (Path path : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String calculated = HashUtil.calculateSHA256(path);
                if (hash.equalsIgnoreCase(calculated)) {
                    localMirrorHashIndex.put(hash.toLowerCase(), path);
                    return path;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private String materializeLocalMirror(FileEntry entry, Path source, Path tempFile) throws Exception {
        if (Files.isDirectory(source)) {
            return zipDirectory(entry, source, tempFile);
        }
        try (InputStream in = Files.newInputStream(source, StandardOpenOption.READ)) {
            return copyStreamToFile(entry, in, tempFile, Files.size(source));
        }
    }

    private String zipDirectory(FileEntry entry, Path sourceDir, Path outputZip) throws Exception {
        byte[] buffer = new byte[262144];
        long copiedBytes = 0L;
        long lastReportAt = 0L;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (OutputStream fileOut = Files.newOutputStream(outputZip,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
             DigestOutputStream digestOut = new DigestOutputStream(fileOut, digest);
             ZipOutputStream zipOut = new ZipOutputStream(digestOut);
             Stream<Path> walk = Files.walk(sourceDir)) {
            for (Path path : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String entryName = sourceDir.relativize(path).toString().replace('\\', '/');
                zipOut.putNextEntry(new ZipEntry(entryName));
                try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        zipOut.write(buffer, 0, bytesRead);
                        copiedBytes += bytesRead;
                        updateActiveTransfer(entry, copiedBytes);
                        long now = System.nanoTime();
                        if (lastReportAt == 0L || now - lastReportAt >= DOWNLOAD_STATUS_REPORT_NANOS) {
                            publishStatus(buildDownloadStatus());
                            lastReportAt = now;
                        }
                    }
                }
                zipOut.closeEntry();
            }
        }
        return bytesToHex(digest.digest());
    }

    private String materializeFromLocalPack(FileEntry entry, Path tempFile) throws Exception {
        if (localPackZip == null || !Files.exists(localPackZip)) {
            return null;
        }

        String relative = entry.getPath();
        if (relative == null || relative.isEmpty()) {
            return null;
        }

        String normalized = relative.replace('\\', '/');
        String[] candidates = new String[] { "Potato_Pack/" + normalized, normalized };

        try (ZipFile zipFile = new ZipFile(localPackZip.toFile())) {
            for (String candidate : candidates) {
                ZipEntry zipEntry = zipFile.getEntry(candidate);
                if (zipEntry == null) {
                    continue;
                }
                try (InputStream in = zipFile.getInputStream(zipEntry)) {
                    beginActiveTransfer(entry);
                    return copyStreamToFile(entry, in, tempFile, zipEntry.getSize());
                }
            }
        } catch (Exception e) {
            cancelActiveTransfer(entry);
            throw e;
        }

        return null;
    }

    private void markPrepared() {
        completedFiles.incrementAndGet();
        publishStatus(buildDownloadStatus());
    }

    private String buildDownloadStatus() {
        DownloadStatusSnapshot snapshot = captureDownloadStatusSnapshot();
        String speedText = snapshot.bytesPerSecond <= 0L ? "0 B/s" : humanBinaryBytes(snapshot.bytesPerSecond) + "/s";
        String countText = snapshot.completedFiles + "/" + snapshot.totalFiles;
        if (snapshot.plannedTransferBytes > 0L && snapshot.resolvedTransferSizes >= snapshot.totalFiles) {
            return "download " + humanBinaryBytes(snapshot.visibleTransferredBytes) + "/" + humanBinaryBytes(snapshot.plannedTransferBytes) + " " + speedText + " " + countText + snapshot.transferLabel;
        }
        if (snapshot.visibleTransferredBytes > 0L) {
            return "download " + humanBinaryBytes(snapshot.visibleTransferredBytes) + " " + speedText + " " + countText + snapshot.transferLabel;
        }
        return "download " + speedText + " " + countText + snapshot.transferLabel;
    }

    private DownloadStatusSnapshot captureDownloadStatusSnapshot() {
        int completed = completedFiles.get();
        int total = totalFiles;
        String activeTransferLabel = resolveActiveTransferLabel();
        String transferLabel = activeTransferLabel == null || activeTransferLabel.isBlank()
                ? (currentTransferLabel == null || currentTransferLabel.isBlank() ? "" : " " + currentTransferLabel)
                : " " + activeTransferLabel;
        long visibleTransferredBytes = currentVisibleTransferredBytes();
        long bytesPerSecond = currentBytesPerSecond(System.nanoTime());
        long plannedBytes;
        int resolvedCount;
        synchronized (plannedBytesLock) {
            plannedBytes = plannedTransferBytes;
            resolvedCount = resolvedTransferSizes.size();
        }
        return new DownloadStatusSnapshot(completed, total, visibleTransferredBytes, plannedBytes,
                resolvedCount, bytesPerSecond, transferLabel);
    }

    private void publishStatus(String status) {
        if (statusConsumer != null && status != null && !status.isEmpty()) {
            lastPublishedStatus = status;
            statusConsumer.accept(status);
        } else if (status != null) {
            lastPublishedStatus = status;
        }
    }

    public String getLiveStatusSnapshot() {
        String status = lastPublishedStatus;
        if (status == null || status.isBlank()) {
            return status;
        }
        if (status.startsWith("download")) {
            return buildDownloadStatus();
        }
        return status;
    }

    public long getLiveTransferredBytesSnapshot() {
        return captureDownloadStatusSnapshot().visibleTransferredBytes;
    }

    public long getLivePlannedTransferBytesSnapshot() {
        DownloadStatusSnapshot snapshot = captureDownloadStatusSnapshot();
        if (snapshot.totalFiles <= 0 || snapshot.resolvedTransferSizes < snapshot.totalFiles) {
            return 0L;
        }
        return snapshot.plannedTransferBytes;
    }

    private void registerResolvedTransferBytes(FileEntry entry, long totalBytes) {
        if (entry == null || totalBytes < 0L) {
            return;
        }
        synchronized (plannedBytesLock) {
            Long previous = resolvedTransferSizes.put(entry, totalBytes);
            plannedTransferBytes += totalBytes - (previous == null ? 0L : previous);
        }
    }

    private void configureDownloadConnection(HttpURLConnection conn, boolean headRequest) throws Exception {
        conn.setConnectTimeout(headRequest ? DOWNLOAD_HEAD_TIMEOUT_MS : DOWNLOAD_CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(headRequest ? DOWNLOAD_HEAD_TIMEOUT_MS : DOWNLOAD_READ_TIMEOUT_MS);
        conn.setUseCaches(false);
        conn.setRequestProperty("Accept-Encoding", "identity");
        conn.setRequestProperty("Connection", "close");
        if (headRequest) {
            conn.setRequestMethod("HEAD");
        } else {
            conn.setRequestMethod("GET");
        }
    }

    private boolean shouldRetryDownload(Exception e) {
        Integer httpStatusCode = extractHttpStatusCode(e);
        if (httpStatusCode != null) {
            int statusCode = httpStatusCode;
            return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500;
        }
        return e instanceof IOException || e instanceof SecurityException;
    }

    private boolean isDiskSpaceFailure(Exception e) {
        if (e == null) {
            return false;
        }
        String message = e.getMessage();
        if (message != null) {
            String lower = message.toLowerCase();
            if (lower.contains("disk space") || lower.contains("not enough space")) {
                return true;
            }
        }
        Throwable cause = e.getCause();
        return cause instanceof Exception exception && isDiskSpaceFailure(exception);
    }

    private HttpURLConnection openDownloadConnection(String downloadUrl) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
        configureDownloadConnection(conn, false);
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    private Integer extractHttpStatusCode(Exception e) {
        if (e instanceof DownloadHttpException httpException) {
            return httpException.getStatusCode();
        }
        String message = e.getMessage();
        if (message == null || !message.startsWith("HTTP ")) {
            return null;
        }
        int spaceIndex = message.indexOf(' ', 5);
        String codeText = spaceIndex > 5 ? message.substring(5, spaceIndex) : message.substring(5).trim();
        try {
            return Integer.parseInt(codeText.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void sleepBeforeRetry() throws IOException {
        try {
            Thread.sleep(DOWNLOAD_RETRY_DELAY_MS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IOException("Download retry interrupted", interruptedException);
        }
    }

    private void resetTransferStats() {
        long now = System.nanoTime();
        transferStartedAtNanos = now;
        synchronized (transferStatsLock) {
            speedSampleStartedAtNanos = now;
            speedSampleStartedBytes = currentVisibleTransferredBytes();
            lastByteProgressAtNanos = now;
            lastMeasuredSpeedBytesPerSecond = 0L;
        }
    }

    private void recordTransferProgress() {
        long now = System.nanoTime();
        synchronized (transferStatsLock) {
            long currentBytes = currentVisibleTransferredBytes();
            if (speedSampleStartedAtNanos == 0L) {
                speedSampleStartedAtNanos = now;
                speedSampleStartedBytes = currentBytes;
            }
            long elapsed = Math.max(1L, now - speedSampleStartedAtNanos);
            long deltaBytes = Math.max(0L, currentBytes - speedSampleStartedBytes);
            lastByteProgressAtNanos = now;
            lastMeasuredSpeedBytesPerSecond = Math.max(0L, Math.round(deltaBytes / (elapsed / 1_000_000_000d)));
            if (elapsed >= 250_000_000L) {
                speedSampleStartedAtNanos = now;
                speedSampleStartedBytes = currentBytes;
            }
        }
    }

    private long currentBytesPerSecond(long now) {
        synchronized (transferStatsLock) {
            if (speedSampleStartedAtNanos == 0L) {
                return 0L;
            }
            if (now - lastByteProgressAtNanos >= DOWNLOAD_SPEED_IDLE_RESET_NANOS) {
                return 0L;
            }
            long currentBytes = currentVisibleTransferredBytes();
            long elapsed = Math.max(1L, now - speedSampleStartedAtNanos);
            long deltaBytes = Math.max(0L, currentBytes - speedSampleStartedBytes);
            if (deltaBytes <= 0L) {
                return lastMeasuredSpeedBytesPerSecond;
            }
            return Math.max(0L, Math.round(deltaBytes / (elapsed / 1_000_000_000d)));
        }
    }

    private void beginActiveTransfer(FileEntry entry) {
        if (entry != null) {
            activeTransferBytes.put(entry, 0L);
            activeTransferStartedAt.putIfAbsent(entry, System.nanoTime());
        }
    }

    private void updateActiveTransfer(FileEntry entry, long copiedBytes) {
        if (entry != null) {
            activeTransferBytes.put(entry, Math.max(0L, copiedBytes));
            recordTransferProgress();
        }
    }

    private void commitActiveTransfer(FileEntry entry) {
        if (entry == null) {
            return;
        }
        Long activeBytes = activeTransferBytes.remove(entry);
        activeTransferStartedAt.remove(entry);
        if (activeBytes != null && activeBytes > 0L) {
            transferredBytes.addAndGet(activeBytes);
            recordTransferProgress();
        }
    }

    private void cancelActiveTransfer(FileEntry entry) {
        if (entry == null) {
            return;
        }
        boolean removed = activeTransferBytes.remove(entry) != null;
        activeTransferStartedAt.remove(entry);
        if (removed) {
            publishStatus(buildDownloadStatus());
        }
    }

    private long currentVisibleTransferredBytes() {
        long total = transferredBytes.get();
        for (Long activeBytes : activeTransferBytes.values()) {
            if (activeBytes != null && activeBytes > 0L) {
                total += activeBytes;
            }
        }
        return total;
    }

    private String resolveActiveTransferLabel() {
        FileEntry selectedEntry = null;
        long selectedStartedAt = Long.MAX_VALUE;
        for (Map.Entry<FileEntry, Long> activeEntry : activeTransferStartedAt.entrySet()) {
            FileEntry entry = activeEntry.getKey();
            if (entry == null || !activeTransferBytes.containsKey(entry)) {
                continue;
            }
            long startedAt = activeEntry.getValue() == null ? Long.MAX_VALUE : activeEntry.getValue();
            if (selectedEntry == null || startedAt < selectedStartedAt) {
                selectedEntry = entry;
                selectedStartedAt = startedAt;
            }
        }
        return selectedEntry == null ? "" : compactName(selectedEntry);
    }

    private void ensureTempWritableSpace(FileEntry entry, long remainingBytesHint) throws IOException {
        long usableSpace;
        try {
            usableSpace = Files.getFileStore(tempDownloadsRoot).getUsableSpace();
        } catch (IOException ignored) {
            return;
        }

        long requiredSpace = DISK_SPACE_HEADROOM_BYTES + Math.max(0L, remainingBytesHint);
        if (usableSpace < requiredSpace) {
            throw new IOException("Insufficient disk space for " + compactName(entry)
                    + " (free " + humanBinaryBytes(usableSpace) + ")");
        }
    }

    private String compactName(FileEntry entry) {
        String name = entry.getFileName();
        if (name == null || name.isEmpty()) {
            String path = entry.getPath();
            if (path == null || path.isEmpty()) {
                return "file";
            }
            int slash = path.lastIndexOf('/');
            name = slash >= 0 ? path.substring(slash + 1) : path;
        }
        if (name.length() <= 28) {
            return name;
        }
        return name.substring(0, 12) + "..." + name.substring(name.length() - 12);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    private void splitEntries(List<FileEntry> source, List<FileEntry> regularEntries, List<FileEntry> modEntries) {
        if (source == null) {
            return;
        }
        for (FileEntry entry : source) {
            if (isModsManagedPath(entry.getPath())) {
                modEntries.add(entry);
            } else {
                regularEntries.add(entry);
            }
        }
    }

    private void splitDeletes(List<DeleteZone.DeleteItem> source,
                              List<DeleteZone.DeleteItem> regularDeletes,
                              List<DeleteZone.DeleteItem> modDeletes) {
        if (source == null) {
            return;
        }
        for (DeleteZone.DeleteItem item : source) {
            if (isModsManagedPath(item.getPath())) {
                modDeletes.add(item);
            } else {
                regularDeletes.add(item);
            }
        }
    }

    private Path stagePendingModsTransaction(List<FileEntry> modEntries,
                                            List<DeleteZone.DeleteItem> modDeletes,
                                            int appliedRegularCount) throws Exception {
        Path gameCoreDir = pathResolver.getGameCoreDirectory();
        Path updaterDir = gameCoreDir.resolve("A_Potato_Updater");
        Path transactionRoot = updaterDir.resolve("mods_transactions");
        Path pendingRoot = transactionRoot.resolve(MODS_PENDING_PREFIX + sessionToken);
        Path payloadRoot = pendingRoot.resolve(MODS_PAYLOAD_DIR);

        Files.createDirectories(transactionRoot);
        cleanupModsTransactionArtifacts(gameCoreDir);
        deleteDirectoryIfExists(pendingRoot);
        Files.createDirectories(payloadRoot);

        int applyIndex = appliedRegularCount;
        for (FileEntry entry : modEntries) {
            applyIndex++;
            publishStatus("apply " + applyIndex + "/" + totalFiles + " " + compactName(entry));
            Path sourceTemp = preparedFiles.remove(entry);
            if (sourceTemp == null) {
                throw new IOException("Missing prepared temp file for " + entry.getPath());
            }

            Path stagedTarget = payloadRoot.resolve(relativeModsPath(entry.getPath())).normalize();
            ensureWithin(payloadRoot, stagedTarget, entry.getPath());
            Files.createDirectories(stagedTarget.getParent());
            Files.move(sourceTemp, stagedTarget, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("  [Stage Mod] -> " + stagedTarget.toAbsolutePath());
        }

        List<String> deleteRelPaths = new ArrayList<>();
        for (DeleteZone.DeleteItem item : modDeletes) {
            deleteRelPaths.add(relativeModsPath(item.getPath()));
        }
        Files.write(pendingRoot.resolve(MODS_DELETE_LIST_FILE), deleteRelPaths, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        return pendingRoot;
    }

    private String relativeModsPath(String path) {
        String normalized = path.replace('\\', '/');
        String prefix = "game_core_dir/mods/";
        if (!normalized.startsWith(prefix)) {
            throw new IllegalArgumentException("Not a mods path: " + path);
        }
        return normalized.substring(prefix.length());
    }

    private boolean isModsManagedPath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.replace('\\', '/');
        return normalized.startsWith("game_core_dir/mods/");
    }

    private void ensureWithin(Path root, Path target, String originalPath) throws IOException {
        if (!target.startsWith(root.normalize())) {
            throw new IOException("Resolved path escapes staging root: " + originalPath);
        }
    }

    private void deleteDirectoryIfExists(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }

    public static void cleanupTempArtifacts(Path gameCoreDir) throws IOException {
        if (gameCoreDir == null) {
            return;
        }
        Path updaterDir = gameCoreDir.resolve("A_Potato_Updater");
        Files.createDirectories(updaterDir);

        Path tempRoot = updaterDir.resolve("temp_downloads");
        if (Files.exists(tempRoot)) {
            deleteDirectoryIfExistsStatic(tempRoot);
        }

        try (Stream<Path> list = Files.list(updaterDir)) {
            for (Path child : (Iterable<Path>) list::iterator) {
                String name = child.getFileName().toString();
                if (Files.isDirectory(child) && name.startsWith(CLEANUP_DIR_PREFIX)) {
                    deleteDirectoryIfExistsStatic(child);
                }
            }
        }
    }

    public static void cleanupModsTransactionArtifacts(Path gameCoreDir) throws IOException {
        if (gameCoreDir == null) {
            return;
        }
        Path transactionRoot = gameCoreDir.resolve("A_Potato_Updater").resolve("mods_transactions");
        if (!Files.isDirectory(transactionRoot)) {
            return;
        }
        try (Stream<Path> list = Files.list(transactionRoot)) {
            for (Path child : (Iterable<Path>) list::iterator) {
                String name = child.getFileName().toString();
                if (!Files.isDirectory(child)) {
                    continue;
                }
                if (name.startsWith(MODS_STAGE_PREFIX)
                        || name.startsWith(MODS_BACKUP_PREFIX)
                        || name.startsWith(MODS_PENDING_PREFIX)) {
                    deleteDirectoryIfExistsStatic(child);
                }
            }
        }
    }

    public static void applyPendingModsTransaction(Path gameCoreDir, Path pendingRoot) throws Exception {
        if (gameCoreDir == null || pendingRoot == null) {
            throw new IOException("Pending mods transaction is missing");
        }

        applyPendingModsTransaction(gameCoreDir, pendingRoot, null, 0, 0);
    }

    public static void applyPendingModsTransaction(Path gameCoreDir,
                                                   Path pendingRoot,
                                                   Consumer<String> statusConsumer,
                                                   int completedBeforeMods,
                                                   int totalFiles) throws Exception {
        if (gameCoreDir == null || pendingRoot == null) {
            throw new IOException("Pending mods transaction is missing");
        }

        Path modsDir = gameCoreDir.resolve("mods");
        Path updaterDir = gameCoreDir.resolve("A_Potato_Updater");
        Path transactionRoot = updaterDir.resolve("mods_transactions");
        Path payloadRoot = pendingRoot.resolve(MODS_PAYLOAD_DIR);
        Path deleteListPath = pendingRoot.resolve(MODS_DELETE_LIST_FILE);
        Path backupModsDir = transactionRoot.resolve(MODS_BACKUP_PREFIX + Long.toHexString(System.nanoTime()));

        if (!Files.isDirectory(payloadRoot)) {
            throw new IOException("Pending staged mods directory not found: " + payloadRoot);
        }

        Files.createDirectories(transactionRoot);
        cleanupTempArtifacts(gameCoreDir);
        deleteDirectoryIfExistsStatic(backupModsDir);

        List<String> replaceRelPaths = collectPayloadRelativePaths(payloadRoot);
        List<String> deleteRelPaths = Files.exists(deleteListPath)
                ? Files.readAllLines(deleteListPath, StandardCharsets.UTF_8)
                : List.of();
        List<String> affectedRelPaths = mergeAffectedPaths(replaceRelPaths, deleteRelPaths);

        Map<String, Boolean> existedBefore = new HashMap<>();
        try {
            Files.createDirectories(modsDir);
            backupAffectedMods(modsDir, backupModsDir, affectedRelPaths, existedBefore, statusConsumer, completedBeforeMods, totalFiles);
            applyAffectedModDeletes(modsDir, deleteRelPaths, statusConsumer, completedBeforeMods, totalFiles);
            applyAffectedModReplacements(modsDir, payloadRoot, replaceRelPaths, statusConsumer, completedBeforeMods, totalFiles);
            deleteDirectoryIfExistsStatic(backupModsDir);
            deleteDirectoryIfExistsStatic(pendingRoot);
        } catch (Exception e) {
            try {
                rollbackAffectedMods(modsDir, backupModsDir, affectedRelPaths, existedBefore);
            } catch (Exception rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        } finally {
            deleteDirectoryIfExistsStatic(backupModsDir);
        }
    }

    private static List<String> collectPayloadRelativePaths(Path payloadRoot) throws IOException {
        List<String> relativePaths = new ArrayList<>();
        if (!Files.isDirectory(payloadRoot)) {
            return relativePaths;
        }

        try (Stream<Path> walk = Files.walk(payloadRoot)) {
            for (Path sourcePath : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                relativePaths.add(payloadRoot.relativize(sourcePath).toString().replace('\\', '/'));
            }
        }
        relativePaths.sort(String::compareTo);
        return relativePaths;
    }

    private static List<String> mergeAffectedPaths(List<String> replaceRelPaths, List<String> deleteRelPaths) {
        List<String> merged = new ArrayList<>();
        for (String path : replaceRelPaths) {
            if (path != null && !path.isBlank() && !merged.contains(path)) {
                merged.add(path);
            }
        }
        for (String path : deleteRelPaths) {
            if (path != null && !path.isBlank() && !merged.contains(path)) {
                merged.add(path);
            }
        }
        merged.sort(String::compareTo);
        return merged;
    }

    private static void backupAffectedMods(Path modsDir,
                                           Path backupRoot,
                                           List<String> affectedRelPaths,
                                           Map<String, Boolean> existedBefore,
                                           Consumer<String> statusConsumer,
                                           int completedBeforeMods,
                                           int totalFiles) throws Exception {
        for (String relativePath : affectedRelPaths) {
            Path sourcePath = modsDir.resolve(relativePath).normalize();
            boolean exists = Files.exists(sourcePath);
            existedBefore.put(relativePath, exists);
            if (!exists) {
                continue;
            }

             publishApplyStatus(statusConsumer, completedBeforeMods, totalFiles, relativePath);

            Path backupPath = backupRoot.resolve(relativePath).normalize();
            if (Files.isDirectory(sourcePath)) {
                copyDirectoryContentsWithRetry(sourcePath, backupPath);
            } else {
                Files.createDirectories(backupPath.getParent());
                copyFileWithRetry(sourcePath, backupPath);
            }
        }
    }

    private static void applyAffectedModDeletes(Path modsDir,
                                                List<String> deleteRelPaths,
                                                Consumer<String> statusConsumer,
                                                int completedBeforeMods,
                                                int totalFiles) throws Exception {
        for (String relativePath : deleteRelPaths) {
            if (relativePath == null || relativePath.isBlank()) {
                continue;
            }
            publishApplyStatus(statusConsumer, completedBeforeMods, totalFiles, relativePath);
            Path targetPath = modsDir.resolve(relativePath).normalize();
            if (Files.isDirectory(targetPath)) {
                deleteDirectoryContentsRecursivelyWithRetry(targetPath);
                Files.deleteIfExists(targetPath);
            } else {
                deletePathWithRetry(targetPath);
            }
        }
    }

    private static void applyAffectedModReplacements(Path modsDir,
                                                     Path payloadRoot,
                                                     List<String> replaceRelPaths,
                                                     Consumer<String> statusConsumer,
                                                     int completedBeforeMods,
                                                     int totalFiles) throws Exception {
        int processed = 0;
        for (String relativePath : replaceRelPaths) {
            if (relativePath == null || relativePath.isBlank()) {
                continue;
            }
            processed++;
            publishApplyStatus(statusConsumer, completedBeforeMods + processed, totalFiles, relativePath);
            Path sourcePath = payloadRoot.resolve(relativePath).normalize();
            Path targetPath = modsDir.resolve(relativePath).normalize();
            Files.createDirectories(targetPath.getParent());
            moveFileWithRetry(sourcePath, targetPath);
        }
    }

    private static void rollbackAffectedMods(Path modsDir,
                                             Path backupRoot,
                                             List<String> affectedRelPaths,
                                             Map<String, Boolean> existedBefore) throws Exception {
        for (String relativePath : affectedRelPaths) {
            Path targetPath = modsDir.resolve(relativePath).normalize();
            boolean existed = existedBefore.getOrDefault(relativePath, false);
            if (!existed) {
                if (Files.isDirectory(targetPath)) {
                    deleteDirectoryContentsRecursivelyWithRetry(targetPath);
                    Files.deleteIfExists(targetPath);
                } else {
                    deletePathWithRetry(targetPath);
                }
                continue;
            }

            Path backupPath = backupRoot.resolve(relativePath).normalize();
            if (!Files.exists(backupPath)) {
                continue;
            }

            if (Files.isDirectory(targetPath)) {
                deleteDirectoryContentsRecursivelyWithRetry(targetPath);
                Files.deleteIfExists(targetPath);
            } else {
                deletePathWithRetry(targetPath);
            }

            if (Files.isDirectory(backupPath)) {
                copyDirectoryContentsWithRetry(backupPath, targetPath);
            } else {
                Files.createDirectories(targetPath.getParent());
                copyFileWithRetry(backupPath, targetPath);
            }
        }
    }

    private static void publishApplyStatus(Consumer<String> statusConsumer, int current, int total, String relativePath) {
        if (statusConsumer == null || total <= 0) {
            return;
        }
        String name = relativePath;
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash < name.length() - 1) {
            name = name.substring(slash + 1);
        }
        if (name.length() > 28) {
            name = name.substring(0, 12) + "..." + name.substring(name.length() - 12);
        }
        statusConsumer.accept("apply " + Math.min(current, total) + "/" + total + " " + name);
    }

    private static void moveDirectoryWithRetry(Path source, Path target) throws Exception {
        long deadline = System.currentTimeMillis() + APPLY_TIMEOUT_MS;
        IOException lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                lastError = e;
                try {
                    Thread.sleep(APPLY_RETRY_DELAY_MS);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Directory move interrupted: " + source, interruptedException);
                }
            }
        }
        throw new IOException("Timed out moving directory " + source + " -> " + target, lastError);
    }

    private static void copyDirectoryContentsWithRetry(Path sourceRoot, Path targetRoot) throws Exception {
        if (!Files.isDirectory(sourceRoot)) {
            return;
        }

        Files.createDirectories(targetRoot);
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            for (Path sourcePath : (Iterable<Path>) walk::iterator) {
                Path relative = sourceRoot.relativize(sourcePath);
                if (relative.toString().isEmpty()) {
                    continue;
                }

                Path targetPath = targetRoot.resolve(relative).normalize();
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    copyFileWithRetry(sourcePath, targetPath);
                }
            }
        }
    }

    private static void clearDirectoryContentsWithRetry(Path root) throws Exception {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (path.equals(root)) {
                    continue;
                }
                deletePathWithRetry(path);
            }
        }
    }

    private static void copyFileWithRetry(Path source, Path target) throws Exception {
        long deadline = System.currentTimeMillis() + APPLY_TIMEOUT_MS;
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
        throw new IOException("Timed out copying file " + source + " -> " + target, lastError);
    }

    private static void moveFileWithRetry(Path source, Path target) throws Exception {
        long deadline = System.currentTimeMillis() + APPLY_TIMEOUT_MS;
        IOException lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                lastError = e;
                Thread.sleep(APPLY_RETRY_DELAY_MS);
            }
        }
        throw new IOException("Timed out moving file " + source + " -> " + target, lastError);
    }

    private static void deletePathWithRetry(Path path) throws Exception {
        long deadline = System.currentTimeMillis() + APPLY_TIMEOUT_MS;
        IOException lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                Files.deleteIfExists(path);
                return;
            } catch (IOException e) {
                lastError = e;
                Thread.sleep(APPLY_RETRY_DELAY_MS);
            }
        }
        throw new IOException("Timed out deleting path " + path, lastError);
    }

    private static void deleteDirectoryContentsRecursivelyWithRetry(Path root) throws Exception {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (path.equals(root)) {
                    continue;
                }
                if (Files.isDirectory(path)) {
                    Files.deleteIfExists(path);
                } else {
                    deletePathWithRetry(path);
                }
            }
        }
    }

    private static void deleteDirectoryIfExistsStatic(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private String humanBinaryBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + "B";
        }

        double value = bytes;
        String[] units = { "KiB", "MiB", "GiB", "TiB" };
        int unitIndex = -1;
        while (value >= 1024.0 && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        return unitIndex <= 0
                ? String.format("%.0f%s", value, units[Math.max(unitIndex, 0)])
                : String.format("%.1f%s", value, units[unitIndex]);
    }
}
