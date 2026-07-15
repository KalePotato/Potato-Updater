package com.potato.updater;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.potato.updater.config.EndpointDefaults;
import com.potato.updater.config.UpdaterConfig;
import com.potato.updater.config.UpdaterConfigManager;
import com.potato.updater.core.DiffEngine;
import com.potato.updater.core.LocalStateManager;
import com.potato.updater.core.ResourcePackOptionsManager;
import com.potato.updater.core.ServerManifestFetcher;
import com.potato.updater.core.SeedSelfUpdater;
import com.potato.updater.core.TaskExecutor;
import com.potato.updater.gui.UpdaterGUI;
import com.potato.updater.model.ModsControl;
import com.potato.updater.model.ServerManifest;
import com.potato.updater.model.StorageTarget;
import com.potato.updater.model.UpdateInfo;
import com.potato.updater.util.PathResolver;
import com.potato.updater.util.UpdaterErrorLogger;

import javax.swing.SwingUtilities;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class PotatoUpdater {

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_ABORT = 1;
    private static final int EXIT_CODE_RESTART_REQUIRED = 2;
    private static final long BASE_LOGO_TIMEOUT_SECONDS = 6L;
    private static final long EXTRA_LOGOS_TIMEOUT_SECONDS = 6L;
    private static final long COMPACT_QUICK_MENU_AVAILABLE_MS = 2800L;
    private static final AtomicReference<Process> ACTIVE_CHILD_PROCESS = new AtomicReference<>();
    private static final AtomicBoolean DETACH_CHILD_ON_EXIT = new AtomicBoolean(false);
    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);

    private record LogoDownloadSpec(UpdaterGUI.LogoSlot slot, String remoteFileName, String cacheFileName) {
    }

    private record LogoDownloadResult(UpdaterGUI.LogoSlot slot, Path path) {
    }

    public static void main(String[] args) {
        if (hasFlag(args, "--smoke-test")) {
            runSmokeTest();
            return;
        }

        if (handleHelperMode(args)) {
            return;
        }

        boolean prelaunchMode = hasFlag(args, "--prelaunch");
        boolean skipStaleCleanup = hasFlag(args, "--skipStaleCleanup");

        System.out.println("=================================================");
        System.out.println("       Potato Updater Core (Standalone)");
        System.out.println("=================================================");

        Path referencePath = Paths.get("").toAbsolutePath();
        boolean gameCoreProvided = false;
        for (int i = 0; i < args.length; i++) {
            if ("--gameCoreDir".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                referencePath = Paths.get(args[i + 1]).toAbsolutePath();
                gameCoreProvided = true;
                break;
            }
        }

        PathResolver pathResolver = new PathResolver();
        try {
            if (gameCoreProvided) {
                pathResolver.initializeFromGameCore(referencePath);
            } else {
                pathResolver.initializeFrom(referencePath);
            }
            System.out.println("[Dir] Game Core: " + pathResolver.getGameCoreDirectory());
            System.out.println("[Dir] MC Upper : " + pathResolver.getMinecraftUpperDirectory());
        } catch (Exception e) {
            System.err.println("[Updater] Failed to initialize path resolver.");
            e.printStackTrace();
            System.exit(EXIT_CODE_ABORT);
            return;
        }

        UpdaterErrorLogger.initialize(pathResolver.getGameCoreDirectory());
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        UpdaterConfigManager configManager = new UpdaterConfigManager(pathResolver.getGameCoreDirectory());
        try {
            configManager.loadOrInitialize();
        } catch (Exception e) {
            UpdaterErrorLogger.logError("Failed to load updater config", e);
        }
        UpdaterConfig updaterConfig = configManager.getConfig();
        if (updaterConfig == null) {
            updaterConfig = new UpdaterConfig();
            configManager.setConfig(updaterConfig);
        }

        CountDownLatch guiLatch = new CountDownLatch(1);
        String startupThemeMode = updaterConfig.isThemeConfigured() ? updaterConfig.getThemeMode() : "light";
        UpdaterGUI gui = new UpdaterGUI(guiLatch, pathResolver, startupThemeMode);
        installShutdownHook();
        gui.setCloseRequestHandler(() -> abortForWindowClose(gui));
        SwingUtilities.invokeLater(() -> gui.showLoadingPhase("boot"));
        if (!updaterConfig.isThemeConfigured()) {
            waitForInitialThemeSettings(gui, updaterConfig, configManager);
        }

        try {
            if (!skipStaleCleanup) {
                terminateStaleUpdaterProcesses(pathResolver.getGameCoreDirectory());
            }
            TaskExecutor.cleanupTempArtifacts(pathResolver.getGameCoreDirectory());
            TaskExecutor.cleanupModsTransactionArtifacts(pathResolver.getGameCoreDirectory());
        } catch (Exception cleanupError) {
            UpdaterErrorLogger.logError("Failed to clean stale updater artifacts on startup", cleanupError);
        }

        runSeedSelfUpdateIfNeeded(gui, pathResolver);

        if (!updaterConfig.isEnabled()) {
            gui.resolveBranding(null);
            gui.awaitBrandingMinimumDisplay(3000);
            closeGuiAndExit(gui);
            return;
        }

        LocalStateManager stateManager = new LocalStateManager(pathResolver, gson);
        ServerManifestFetcher fetcher = new ServerManifestFetcher(gson);
        String targetBaseUrl = resolveTargetBaseUrl(gui, fetcher, updaterConfig);
        if (targetBaseUrl == null || targetBaseUrl.isEmpty()) {
            gui.resolveBranding(null);
            gui.awaitBrandingMinimumDisplay(3000);
            shrinkToCompactError(gui, "route failed");
            waitForUserErrorDecision(gui);
            return;
        }

        ServerManifest serverManifest;
        while (true) {
            stateManager.load();
            String localTime = stateManager.getCurrentState().getLastOperationTime();

            resolveBranding(gui, fetcher, updaterConfig, pathResolver, targetBaseUrl);
            if (handleForceRescanRequest(gui, stateManager)) {
                continue;
            }

            SwingUtilities.invokeLater(() -> gui.updateStatusText("fetch list"));
            serverManifest = fetcher.fetchManifest(targetBaseUrl + "list.json");
            if (serverManifest == null) {
                gui.awaitBrandingMinimumDisplay(3000);
                if (handleForceRescanRequest(gui, stateManager)) {
                    continue;
                }
                shrinkToCompactError(gui, "list failed");
                waitForUserErrorDecision(gui);
                return;
            }

            if (handleForceRescanRequest(gui, stateManager)) {
                continue;
            }

            if (!stateManager.getCurrentState().isLastUpdateFailed()
                    && serverManifest.getOperationTime() != null
                    && serverManifest.getOperationTime().equals(localTime)) {
                gui.awaitBrandingMinimumDisplay(3000);
                if (handleForceRescanRequest(gui, stateManager)) {
                    continue;
                }
                if (waitForCompactWindowMenu(gui, configManager, updaterConfig, stateManager, pathResolver, prelaunchMode, true)) {
                    continue;
                }
                closeGuiAndExit(gui);
                return;
            }

            SwingUtilities.invokeLater(() -> gui.updateStatusText("load info"));
            UpdateInfo info = fetcher.fetchUpdateInfo(targetBaseUrl + "info.json");
            if (info != null) {
                SwingUtilities.invokeLater(() -> gui.setUpdateInfo(info));
            }

            Set<String> optionalResourcePacks = fetcher.fetchOptionalResourcePacks(targetBaseUrl + "optional_resource_packs.json");
            SwingUtilities.invokeLater(() -> gui.setOptionalResourcePackNames(optionalResourcePacks));

            ModsControl modsControl = fetcher.fetchModsControl(targetBaseUrl + "mods_control.json");
            SwingUtilities.invokeLater(() -> gui.setModsControl(modsControl));

            gui.awaitBrandingMinimumDisplay(3000);
            if (handleForceRescanRequest(gui, stateManager)) {
                continue;
            }
            if (waitForCompactWindowMenu(gui, configManager, updaterConfig, stateManager, pathResolver, prelaunchMode, false)) {
                continue;
            }
            break;
        }

        SwingUtilities.invokeLater(() -> gui.executeExpansionAndShowReview(new DiffEngine.DiffResult()));
        try {
            gui.getScanLatch().await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeGuiAndExit(gui);
            return;
        }

        DiffEngine.DiffResult scannedResult = runScan(gui, pathResolver, serverManifest);
        if (scannedResult == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            gui.updateScanningStatus("ready");
            gui.populateReviewData(scannedResult);
        });

        try {
            guiLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeGuiAndExit(gui);
            return;
        }

        if (!gui.isUserConfirmed()) {
            closeGuiAndExit(gui);
            return;
        }

        DiffEngine.DiffResult finalResult = gui.getApprovedResult();
        if (!finalResult.hasChanges()) {
            try {
                stateManager.commitTransaction(serverManifest.getOperationTime());
            } catch (Exception e) {
                UpdaterErrorLogger.logError("Failed to commit zero-change update after review confirmation", e);
            }
            SwingUtilities.invokeLater(() -> gui.shrinkToLoadingPhase("up to date"));
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            SwingUtilities.invokeLater(() -> gui.showFinishState(true, "up to date"));
            waitForUserSuccessDecision(gui);
            return;
        }

        SwingUtilities.invokeLater(() -> gui.updateStatusText("deploy"));

        AtomicLong lastStageProgressAt = new AtomicLong(System.currentTimeMillis());
        AtomicReference<String> liveStatus = new AtomicReference<>("download");
        AtomicReference<TaskExecutor> executorRef = new AtomicReference<>();
        TaskExecutor executor = new TaskExecutor(pathResolver, targetBaseUrl, status -> {
            liveStatus.set(status);
            lastStageProgressAt.set(System.currentTimeMillis());
            TaskExecutor currentExecutor = executorRef.get();
            long transferredBytes = currentExecutor == null ? 0L : currentExecutor.getLiveTransferredBytesSnapshot();
            long plannedBytes = currentExecutor == null ? 0L : currentExecutor.getLivePlannedTransferBytesSnapshot();
            SwingUtilities.invokeLater(() -> {
                gui.clearPersistentWarningText();
                gui.updateStatusText(status);
                gui.updateCompactProgressForStatus(status, transferredBytes, plannedBytes);
            });
        }, prelaunchMode);
        executorRef.set(executor);

        ExecutorService updateExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "potato-update-exec");
            thread.setDaemon(true);
            return thread;
        });

        Future<TaskExecutor.UpdateResult> updateFuture = updateExecutor.submit(() -> executor.executeUpdate(finalResult));
        TaskExecutor.UpdateResult updateResult = TaskExecutor.UpdateResult.failed();
        boolean timedOut = false;
        try {
            while (true) {
                try {
                    updateResult = updateFuture.get(250, TimeUnit.MILLISECONDS);
                    break;
                } catch (TimeoutException ignored) {
                    String snapshotStatus = executor.getLiveStatusSnapshot();
                    if (snapshotStatus != null && !snapshotStatus.isBlank()) {
                        liveStatus.set(snapshotStatus);
                        long transferredBytes = executor.getLiveTransferredBytesSnapshot();
                        long plannedBytes = executor.getLivePlannedTransferBytesSnapshot();
                        SwingUtilities.invokeLater(() -> {
                            gui.updateStatusText(snapshotStatus);
                            gui.updateCompactProgressForStatus(snapshotStatus, transferredBytes, plannedBytes);
                        });
                    }
                    long idleMs = System.currentTimeMillis() - lastStageProgressAt.get();
                    String status = snapshotStatus != null && !snapshotStatus.isBlank() ? snapshotStatus : liveStatus.get();
                    if (status != null && status.startsWith("download")) {
                        if (idleMs > 30_000L) {
                            SwingUtilities.invokeLater(() -> gui.setPersistentWarningText("download too long, check network"));
                        }
                        if (idleMs > 120_000L) {
                            timedOut = true;
                            updateFuture.cancel(true);
                            break;
                        }
                    } else if (status != null && status.startsWith("apply")) {
                        if (idleMs > 20_000L) {
                            SwingUtilities.invokeLater(() -> gui.setPersistentWarningText("apply timeout, retry later"));
                        }
                        if (idleMs > 180_000L) {
                            timedOut = true;
                            updateFuture.cancel(true);
                            break;
                        }
                    } else if (status != null && status.startsWith("finalize")) {
                        if (idleMs > 8_000L) {
                            SwingUtilities.invokeLater(() -> gui.setPersistentWarningText("finalize timeout, retry later"));
                        }
                        if (idleMs > 60_000L) {
                            timedOut = true;
                            updateFuture.cancel(true);
                            break;
                        }
                    }
                }
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            UpdaterErrorLogger.logError("Task executor crashed while deploying files", cause != null ? cause : e);
            shrinkToCompactError(gui, "deploy failed");
            waitForUserErrorDecision(gui);
            updateExecutor.shutdownNow();
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeGuiAndExit(gui);
            updateExecutor.shutdownNow();
            return;
        } finally {
            updateExecutor.shutdownNow();
        }

        if (timedOut) {
            SwingUtilities.invokeLater(() -> gui.setPersistentWarningText("run timeout, retry later"));
            shrinkToCompactError(gui, "deploy failed");
            waitForUserErrorDecision(gui);
            return;
        }

        if (updateResult.getStatus() == TaskExecutor.UpdateResult.Status.SUCCESS) {
            try {
                applySelectedResourcePacks(gui, pathResolver, finalResult);
                stateManager.commitTransaction(serverManifest.getOperationTime());
                SwingUtilities.invokeLater(() -> gui.showFinishState(true, "updated"));
                waitForUserSuccessDecision(gui);
            } catch (Exception e) {
                UpdaterErrorLogger.logError("Failed during post-deploy finalization", e);
                try {
                    stateManager.saveFailureState();
                } catch (Exception saveFailureError) {
                    UpdaterErrorLogger.logError("Failed to save local failure state after finalization error", saveFailureError);
                }
                shrinkToCompactError(gui, "resource pack install failed");
                waitForUserErrorDecision(gui);
            }
            return;
        }

        if (updateResult.getStatus() == TaskExecutor.UpdateResult.Status.RESTART_REQUIRED) {
            try {
                applySelectedResourcePacks(gui, pathResolver, finalResult);
                launchDeferredModsApplier(pathResolver.getGameCoreDirectory(), updateResult.getPendingModsDir());
                SwingUtilities.invokeLater(() -> gui.showFinishState(true, "restart required"));
                try {
                    Thread.sleep(2200L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (gui != null) {
                    gui.awaitProgressCompletion(1200L);
                    gui.fadeOutAndDispose();
                }
                System.exit(EXIT_CODE_RESTART_REQUIRED);
                return;
            } catch (Exception e) {
                UpdaterErrorLogger.logError("Failed during restart-required finalization", e);
                try {
                    stateManager.saveFailureState();
                } catch (Exception saveFailureError) {
                    UpdaterErrorLogger.logError("Failed to save local failure state after restart finalization error", saveFailureError);
                }
                shrinkToCompactError(gui, "deploy failed");
                waitForUserErrorDecision(gui);
                return;
            }
        }

        try {
            stateManager.saveFailureState();
        } catch (Exception e) {
            UpdaterErrorLogger.logError("Failed to save local failure state", e);
        }
        shrinkToCompactError(gui, "deploy failed");
        waitForUserErrorDecision(gui);
    }

    private static void runSmokeTest() {
        try {
            URI seedConfigUri = URI.create(EndpointDefaults.seedConfigUrl());
            URI storageConfigUri = URI.create(EndpointDefaults.storageConfigUrl());
            if (!seedConfigUri.isAbsolute() || seedConfigUri.getHost() == null
                    || !storageConfigUri.isAbsolute() || storageConfigUri.getHost() == null) {
                throw new IllegalStateException("Build endpoints are not absolute URLs");
            }
            if (!seedConfigUri.getHost().equalsIgnoreCase(storageConfigUri.getHost())) {
                throw new IllegalStateException("Build endpoints do not use the same sync host");
            }

            new Gson().toJson(new UpdaterConfig());
            Class.forName("com.formdev.flatlaf.FlatLaf", false, PotatoUpdater.class.getClassLoader());
            System.out.println("POTATO_UPDATER_SMOKE_TEST_OK");
        } catch (Throwable error) {
            System.err.println("POTATO_UPDATER_SMOKE_TEST_FAILED: " + error.getMessage());
            error.printStackTrace();
            System.exit(EXIT_CODE_ABORT);
        }
    }

    private static void runSeedSelfUpdateIfNeeded(UpdaterGUI gui, PathResolver pathResolver) {
        if (gui == null || pathResolver == null || pathResolver.getGameCoreDirectory() == null) {
            return;
        }

        SeedSelfUpdater seedSelfUpdater = new SeedSelfUpdater(pathResolver.getGameCoreDirectory());
        SeedSelfUpdater.CheckResult checkResult;
        try {
            SwingUtilities.invokeLater(() -> gui.updateStatusText("seed check"));
            checkResult = seedSelfUpdater.checkForUpdate();
        } catch (Exception e) {
            UpdaterErrorLogger.logError("Seed self-update check failed; continuing updater flow", e);
            return;
        }

        if (checkResult == null || !checkResult.isUpdateAvailable()) {
            return;
        }

        if (!showSeedUpdateRequiredOnEdt(gui, checkResult.getRemoteVersion())) {
            return;
        }

        while (true) {
            try {
                SwingUtilities.invokeLater(() -> gui.updateStatusText("seed download"));
                SeedSelfUpdater.PreparedUpdate preparedUpdate = seedSelfUpdater.preparePendingUpdate(checkResult);
                launchSeedUpdateHelper(pathResolver.getGameCoreDirectory(), preparedUpdate.getPendingDir());
                SwingUtilities.invokeLater(() -> gui.showFinishState(true, "restart required"));
                try {
                    Thread.sleep(1800L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (gui != null) {
                    gui.awaitProgressCompletion(1200L);
                    gui.fadeOutAndDispose();
                }
                System.exit(EXIT_CODE_RESTART_REQUIRED);
                return;
            } catch (Exception e) {
                UpdaterErrorLogger.logError("Seed self-update preparation failed", e);
                if (!showSeedUpdateFailedOnEdt(gui, "Potato Seed更新失败，请检查网络后重试。")) {
                    return;
                }
            }
        }
    }

    private static boolean showSeedUpdateRequiredOnEdt(UpdaterGUI gui, String remoteVersion) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try {
                gui.showSeedUpdateRequiredDialog(remoteVersion, result::complete);
            } catch (Exception e) {
                UpdaterErrorLogger.logError("Failed to show Seed update prompt", e);
                result.complete(false);
            }
        });
        return waitForBooleanResult(result, false);
    }

    private static boolean showSeedUpdateFailedOnEdt(UpdaterGUI gui, String message) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try {
                gui.showSeedUpdateFailedDialog(message, result::complete);
            } catch (Exception e) {
                UpdaterErrorLogger.logError("Failed to show Seed update failure prompt", e);
                result.complete(false);
            }
        });
        return waitForBooleanResult(result, false);
    }

    private static boolean waitForBooleanResult(CompletableFuture<Boolean> result, boolean fallback) {
        try {
            return Boolean.TRUE.equals(result.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (ExecutionException e) {
            UpdaterErrorLogger.logError("Failed while waiting for Seed update dialog", e.getCause() != null ? e.getCause() : e);
            return fallback;
        }
    }

    private static void applySelectedResourcePacks(UpdaterGUI gui,
                                                   PathResolver pathResolver,
                                                   DiffEngine.DiffResult result) throws Exception {
        if (result == null || !result.hasResourcePackOptionScope()) {
            return;
        }

        if (gui != null) {
            SwingUtilities.invokeLater(() -> gui.updateStatusText("正在安装资源包"));
        }
        ResourcePackOptionsManager optionsManager = new ResourcePackOptionsManager(pathResolver);
        optionsManager.applySelections(result.resourcePackOptionScope, result.resourcePacksToInstall);
    }

    private static DiffEngine.DiffResult runScan(UpdaterGUI gui, PathResolver pathResolver, ServerManifest manifest) {
        SwingUtilities.invokeLater(() -> gui.updateScanningStatus("hash check"));
        DiffEngine diffEngine = new DiffEngine();
        ExecutorService scanExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "potato-diff-scan");
            thread.setDaemon(true);
            return thread;
        });
        Future<DiffEngine.DiffResult> scanFuture = scanExecutor.submit(() ->
                diffEngine.calculateDiff(manifest, pathResolver,
                        (current, total, path, processedBytes, totalBytes) ->
                                gui.updateScanningProgress(current, total, path, processedBytes, totalBytes)));

        try {
            while (true) {
                if (gui.isScanCancelled()) {
                    scanFuture.cancel(true);
                    scanExecutor.shutdownNow();
                    closeGuiAndExit(gui);
                    return null;
                }

                try {
                    return scanFuture.get(60, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ignored) {
                }
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause != null && "Scan cancelled".equals(cause.getMessage())) {
                closeGuiAndExit(gui);
                return null;
            }
            UpdaterErrorLogger.logError("Failed while scanning local files", e);
            shrinkToCompactError(gui, "scan failed");
            waitForUserErrorDecision(gui);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeGuiAndExit(gui);
            return null;
        } finally {
            scanExecutor.shutdownNow();
        }
    }

    private static String resolveTargetBaseUrl(UpdaterGUI gui,
                                               ServerManifestFetcher fetcher,
                                               UpdaterConfig updaterConfig) {
        String targetBaseUrl;
        if (updaterConfig.isEnableStorageTargetUrl()) {
            SwingUtilities.invokeLater(() -> gui.updateStatusText("route"));
            StorageTarget storageTarget = fetcher.fetchStorageTarget(updaterConfig.getStorageTargetUrl());
            if (storageTarget == null || storageTarget.getTarget() == null || storageTarget.getTarget().isEmpty()) {
                return null;
            }
            targetBaseUrl = storageTarget.getTarget();
        } else {
            SwingUtilities.invokeLater(() -> gui.updateStatusText("static route"));
            targetBaseUrl = updaterConfig.getCustomTargetUrl();
            if (targetBaseUrl == null || targetBaseUrl.isEmpty()) {
                return null;
            }
        }

        if (!targetBaseUrl.endsWith("/")) {
            targetBaseUrl += "/";
        }
        if (!targetBaseUrl.endsWith("Potato_Pack/")) {
            targetBaseUrl += "Potato_Pack/";
        }
        return targetBaseUrl;
    }

    private static void resolveBranding(UpdaterGUI gui,
                                        ServerManifestFetcher fetcher,
                                        UpdaterConfig updaterConfig,
                                        PathResolver pathResolver,
                                        String targetBaseUrl) {
        if (!updaterConfig.isEnableLogo()) {
            gui.resolveBranding(null);
            return;
        }

        SwingUtilities.invokeLater(() -> gui.updateStatusText("load logo"));
        Path logoTempPath = pathResolver.getGameCoreDirectory().resolve("A_Potato_Updater").resolve("cache");
        try {
            Path localLogo = CompletableFuture.supplyAsync(() ->
                    fetcher.downloadLogo(targetBaseUrl + "logo.png", logoTempPath, "logo_cache.png"))
                    .get(BASE_LOGO_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (localLogo == null) {
                gui.resolveBranding(null);
                return;
            }
            Map<UpdaterGUI.LogoSlot, Path> localLogos = new EnumMap<>(UpdaterGUI.LogoSlot.class);
            localLogos.put(UpdaterGUI.LogoSlot.SMALL_LIGHT, localLogo);
            collectOptionalBrandingLogos(fetcher, targetBaseUrl, logoTempPath, localLogos);
            gui.resolveBrandingLogos(localLogos);
        } catch (Exception e) {
            gui.resolveBranding(null);
        }
    }

    private static void collectOptionalBrandingLogos(ServerManifestFetcher fetcher,
                                                     String targetBaseUrl,
                                                     Path logoTempPath,
                                                     Map<UpdaterGUI.LogoSlot, Path> localLogos) {
        List<LogoDownloadSpec> specs = List.of(
                new LogoDownloadSpec(UpdaterGUI.LogoSlot.SMALL_DARK, "logo_dark.png", "logo_dark_cache.png"),
                new LogoDownloadSpec(UpdaterGUI.LogoSlot.LARGE_LIGHT, "logo_huge.png", "logo_huge_cache.png"),
                new LogoDownloadSpec(UpdaterGUI.LogoSlot.LARGE_DARK, "logo_huge_dark.png", "logo_huge_dark_cache.png")
        );
        List<CompletableFuture<LogoDownloadResult>> downloads = new ArrayList<>();
        for (LogoDownloadSpec spec : specs) {
            downloads.add(CompletableFuture.supplyAsync(() -> downloadLogoSpec(fetcher, targetBaseUrl, logoTempPath, spec)));
        }

        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(EXTRA_LOGOS_TIMEOUT_SECONDS);
        for (CompletableFuture<LogoDownloadResult> download : downloads) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                download.cancel(true);
                continue;
            }
            try {
                LogoDownloadResult result = download.get(remainingNanos, TimeUnit.NANOSECONDS);
                if (result != null && result.path() != null) {
                    localLogos.put(result.slot(), result.path());
                }
            } catch (Exception ignored) {
                download.cancel(true);
            }
        }
    }

    private static LogoDownloadResult downloadLogoSpec(ServerManifestFetcher fetcher,
                                                       String targetBaseUrl,
                                                       Path logoTempPath,
                                                       LogoDownloadSpec spec) {
        Path path = fetcher.downloadLogo(targetBaseUrl + spec.remoteFileName(), logoTempPath, spec.cacheFileName());
        return new LogoDownloadResult(spec.slot(), path);
    }

    private static boolean handleForceRescanRequest(UpdaterGUI gui, LocalStateManager stateManager) {
        if (gui == null || stateManager == null || !gui.consumeForceRescanRequested()) {
            return false;
        }

        try {
            stateManager.resetState();
            SwingUtilities.invokeLater(gui::prepareForForcedRescan);
            return true;
        } catch (Exception e) {
            UpdaterErrorLogger.logError("Failed to reset local updater version state for forced rescan", e);
            shrinkToCompactError(gui, "force check failed");
            waitForUserErrorDecision(gui);
            return false;
        }
    }

    private static boolean waitForCompactWindowMenu(UpdaterGUI gui,
                                                    UpdaterConfigManager configManager,
                                                    UpdaterConfig updaterConfig,
                                                    LocalStateManager stateManager,
                                                    PathResolver pathResolver,
                                                    boolean prelaunchMode,
                                                    boolean manualSyncAllowed) {
        if (gui == null || stateManager == null || pathResolver == null) {
            return false;
        }
        try {
            SwingUtilities.invokeAndWait(() -> gui.setCompactQuickMenuAvailable(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (InvocationTargetException e) {
            UpdaterErrorLogger.logError("Failed to enable compact updater menu affordance", e.getCause() != null ? e.getCause() : e);
            return false;
        }
        long deadline = System.currentTimeMillis() + COMPACT_QUICK_MENU_AVAILABLE_MS;
        try {
            while (System.currentTimeMillis() < deadline) {
                if (gui.consumeQuickMenuRequested()) {
                    UpdaterGUI.QuickMenuResult result = showQuickMenuOnEdt(gui, manualSyncAllowed, updaterConfig, configManager);
                    if (result == UpdaterGUI.QuickMenuResult.MANUAL_SYNC) {
                        runManualSyncRelaunch(gui, stateManager, pathResolver, prelaunchMode);
                        return false;
                    }
                    return false;
                }
                if (gui.isCompactQuickMenuInteractionActive()) {
                    deadline = Math.max(deadline, System.currentTimeMillis() + 350L);
                }
                try {
                    Thread.sleep(20L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        } finally {
            SwingUtilities.invokeLater(() -> gui.setCompactQuickMenuAvailable(false));
        }
        return false;
    }

    private static UpdaterGUI.QuickMenuResult showQuickMenuOnEdt(UpdaterGUI gui,
                                                                 boolean manualSyncAllowed,
                                                                 UpdaterConfig updaterConfig,
                                                                 UpdaterConfigManager configManager) {
        CompletableFuture<UpdaterGUI.QuickMenuResult> result = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try {
                gui.showQuickMenuDialog(manualSyncAllowed, updaterConfig, configManager, result::complete);
            } catch (Exception e) {
                UpdaterErrorLogger.logError("Failed to show updater menu", e);
                result.complete(UpdaterGUI.QuickMenuResult.CONTINUE);
            }
        });
        try {
            return result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return UpdaterGUI.QuickMenuResult.CONTINUE;
        } catch (ExecutionException e) {
            UpdaterErrorLogger.logError("Failed while waiting for updater menu", e.getCause() != null ? e.getCause() : e);
            return UpdaterGUI.QuickMenuResult.CONTINUE;
        }
    }

    private static void runManualSyncRelaunch(UpdaterGUI gui,
                                              LocalStateManager stateManager,
                                              PathResolver pathResolver,
                                              boolean prelaunchMode) {
        try {
            stateManager.resetState();
            relaunchForForceRescan(gui, pathResolver, prelaunchMode);
        } catch (Exception e) {
            UpdaterErrorLogger.logError("Failed to restart updater for manual sync", e);
            shrinkToCompactError(gui, "force check failed");
            waitForUserErrorDecision(gui);
        }
    }

    private static void waitForInitialThemeSettings(UpdaterGUI gui,
                                                    UpdaterConfig updaterConfig,
                                                    UpdaterConfigManager configManager) {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try {
                gui.showInitialThemeSettingsDialog(updaterConfig, configManager, () -> completion.complete(null));
            } catch (Exception e) {
                UpdaterErrorLogger.logError("Failed to show initial theme settings", e);
                completion.complete(null);
            }
        });
        try {
            completion.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            UpdaterErrorLogger.logError("Failed while waiting for initial theme settings",
                    e.getCause() != null ? e.getCause() : e);
        }
    }

    private static void relaunchForForceRescan(UpdaterGUI gui,
                                               PathResolver pathResolver,
                                               boolean prelaunchMode) throws Exception {
        Path updaterJar = Paths.get(PotatoUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toAbsolutePath()
                .normalize();

        ProcessBuilder builder = new ProcessBuilder(
                resolveJavaExecutable(),
                "-jar",
                updaterJar.toString(),
                "--gameCoreDir",
                pathResolver.getGameCoreDirectory().toAbsolutePath().toString(),
                "--skipStaleCleanup");

        if (prelaunchMode) {
            builder.command().add("--prelaunch");
        }

        builder.directory(pathResolver.getGameCoreDirectory().toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(
                pathResolver.getGameCoreDirectory()
                        .resolve("A_Potato_Updater")
                        .resolve("force_rescan_relaunch.log")
                        .toFile()));

        if (gui != null) {
            gui.awaitProgressCompletion(200L);
            gui.fadeOutAndDispose();
        }

        DETACH_CHILD_ON_EXIT.set(false);
        Process relaunched = builder.start();
        ACTIVE_CHILD_PROCESS.set(relaunched);
        int exitCode = relaunched.waitFor();
        ACTIVE_CHILD_PROCESS.compareAndSet(relaunched, null);
        System.exit(exitCode);
    }

    private static void waitForUserSuccessDecision(UpdaterGUI gui) {
        try {
            Thread.sleep(3000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        closeGuiAndExit(gui);
    }

    private static void waitForUserErrorDecision(UpdaterGUI gui) {
        try {
            gui.updateStatusText("error");
            while (!gui.isDecisionMade()) {
                Thread.sleep(100L);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (gui.isUserForceContinue()) {
            closeGuiAndExit(gui);
        } else {
            gui.fadeOutAndDispose();
            System.exit(EXIT_CODE_ABORT);
        }
    }

    private static void closeGuiAndExit(UpdaterGUI gui) {
        if (gui != null) {
            gui.awaitProgressCompletion(1200L);
            gui.fadeOutAndDispose();
        }
        try {
            Thread.sleep(800L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.exit(EXIT_CODE_SUCCESS);
    }

    private static void shrinkToCompactError(UpdaterGUI gui, String message) {
        if (gui == null) {
            return;
        }
        try {
            SwingUtilities.invokeAndWait(() -> gui.shrinkToLoadingPhase("error"));
            Thread.sleep(420L);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
        SwingUtilities.invokeLater(() -> gui.showFinishState(false, message));
    }

    private static boolean handleHelperMode(String[] args) {
        String applyPendingSeedDir = getArgValue(args, "--applyPendingSeedUpdate");
        String applyPendingDir = getArgValue(args, "--applyPendingModsDir");
        String gameCoreDir = getArgValue(args, "--gameCoreDir");
        if (gameCoreDir == null || (applyPendingSeedDir == null && applyPendingDir == null)) {
            return false;
        }

        Path gameCorePath = Paths.get(gameCoreDir).toAbsolutePath().normalize();
        UpdaterErrorLogger.initialize(gameCorePath);

        try {
            long waitPid = parseLongOrDefault(getArgValue(args, "--waitPid"), -1L);
            waitForRelatedProcessesToExit(gameCorePath, waitPid, 10 * 60_000L);
            if (applyPendingSeedDir != null) {
                System.setProperty("java.awt.headless", "false");
                SeedSelfUpdater.applyPendingSeedUpdate(gameCorePath,
                        Paths.get(applyPendingSeedDir).toAbsolutePath().normalize());
                UpdaterGUI.showStandaloneSeedUpdateMessage("Potato Seed",
                        "更新完成，请重新启动游戏",
                        true);
            } else {
                TaskExecutor.applyPendingModsTransaction(gameCorePath,
                        Paths.get(applyPendingDir).toAbsolutePath().normalize());
                TaskExecutor.cleanupTempArtifacts(gameCorePath);
                TaskExecutor.cleanupModsTransactionArtifacts(gameCorePath);
            }
            System.exit(EXIT_CODE_SUCCESS);
        } catch (Exception e) {
            String context = applyPendingSeedDir != null
                    ? "Deferred Seed self-update helper failed"
                    : "Deferred mods apply helper failed";
            UpdaterErrorLogger.logError(context, e);
            if (applyPendingSeedDir != null) {
                UpdaterGUI.showStandaloneSeedUpdateMessage("Potato Seed",
                        "Potato Seed更新失败，请重新启动游戏后重试",
                        false);
            }
            System.exit(EXIT_CODE_ABORT);
        }
        return true;
    }

    private static void launchSeedUpdateHelper(Path gameCoreDir, Path pendingSeedDir) throws Exception {
        if (gameCoreDir == null || pendingSeedDir == null) {
            throw new IllegalArgumentException("Missing pending Seed update path");
        }

        Path updaterJar = Paths.get(PotatoUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        long waitPid = resolveLockOwnerPid();
        ProcessBuilder builder = new ProcessBuilder(
                resolveJavaExecutable(),
                "-jar",
                updaterJar.toAbsolutePath().toString(),
                "--gameCoreDir",
                gameCoreDir.toAbsolutePath().toString(),
                "--applyPendingSeedUpdate",
                pendingSeedDir.toAbsolutePath().toString(),
                "--waitPid",
                Long.toString(waitPid));
        builder.directory(gameCoreDir.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(
                gameCoreDir.resolve("A_Potato_Updater").resolve("seed_self_update.log").toFile()));
        Process helper = builder.start();
        ACTIVE_CHILD_PROCESS.set(helper);
        DETACH_CHILD_ON_EXIT.set(true);
    }

    private static void launchDeferredModsApplier(Path gameCoreDir, Path pendingModsDir) throws Exception {
        if (gameCoreDir == null || pendingModsDir == null) {
            throw new IllegalArgumentException("Missing pending mods transaction path");
        }

        Path updaterJar = Paths.get(PotatoUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        long waitPid = resolveLockOwnerPid();
        ProcessBuilder builder = new ProcessBuilder(
                resolveJavaExecutable(),
                "-jar",
                updaterJar.toAbsolutePath().toString(),
                "--gameCoreDir",
                gameCoreDir.toAbsolutePath().toString(),
                "--applyPendingModsDir",
                pendingModsDir.toAbsolutePath().toString(),
                "--waitPid",
                Long.toString(waitPid));
        builder.directory(gameCoreDir.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(
                gameCoreDir.resolve("A_Potato_Updater").resolve("deferred_apply.log").toFile()));
        Process helper = builder.start();
        ACTIVE_CHILD_PROCESS.set(helper);
        DETACH_CHILD_ON_EXIT.set(true);
    }

    private static String resolveJavaExecutable() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String executable = os.contains("win") ? "java.exe" : "java";
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + executable;
        File candidate = new File(javaBin);
        if (candidate.exists()) {
            return candidate.getAbsolutePath();
        }
        executable = os.contains("win") ? "java.exe" : "java";
        return System.getProperty("java.home") + File.separator + "bin" + File.separator + executable;
    }

    private static long resolveLockOwnerPid() {
        long currentPid = ProcessHandle.current().pid();
        return ProcessHandle.current()
                .parent()
                .filter(parent -> parent.info().command()
                        .map(command -> command.toLowerCase().contains("java"))
                        .orElse(false))
                .map(ProcessHandle::pid)
                .orElse(currentPid);
    }

    private static void waitForRelatedProcessesToExit(Path gameCorePath, long pid, long timeoutMs) throws InterruptedException {
        long startedAt = System.currentTimeMillis();
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            if (!hasRelatedProcess(gameCorePath, pid)) {
                return;
            }
            Thread.sleep(250L);
        }
    }

    private static boolean hasRelatedProcess(Path gameCorePath, long waitedPid) {
        String gameCore = gameCorePath.toAbsolutePath().normalize().toString().toLowerCase();
        long selfPid = ProcessHandle.current().pid();
        for (ProcessHandle handle : ProcessHandle.allProcesses().toList()) {
            if (handle.pid() == selfPid) {
                continue;
            }
            if (!handle.isAlive()) {
                continue;
            }
            if (waitedPid > 0L && handle.pid() == waitedPid) {
                return true;
            }
            String commandLine = handle.info().commandLine().orElse("").toLowerCase();
            if (commandLine.isEmpty()) {
                continue;
            }
            if (commandLine.contains(gameCore) && commandLine.contains("java")) {
                return true;
            }
        }
        return false;
    }

    private static void terminateStaleUpdaterProcesses(Path gameCorePath) {
        String gameCore = gameCorePath.toAbsolutePath().normalize().toString().toLowerCase();
        long selfPid = ProcessHandle.current().pid();
        for (ProcessHandle handle : ProcessHandle.allProcesses().toList()) {
            if (handle.pid() == selfPid || !handle.isAlive()) {
                continue;
            }
            String commandLine = handle.info().commandLine().orElse("").toLowerCase();
            if (!commandLine.contains("potato_updater.jar")) {
                continue;
            }
            if (!commandLine.contains(gameCore)) {
                continue;
            }
            destroyProcessHandle(handle);
        }
    }

    private static void abortForWindowClose(UpdaterGUI gui) {
        DETACH_CHILD_ON_EXIT.set(false);
        destroyActiveChildProcess();
        if (gui != null) {
            gui.disposeSilently();
        }
        System.exit(EXIT_CODE_ABORT);
    }

    private static void installShutdownHook() {
        if (!SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!DETACH_CHILD_ON_EXIT.get()) {
                destroyActiveChildProcess();
            }
        }, "potato-updater-shutdown"));
    }

    private static void destroyActiveChildProcess() {
        Process process = ACTIVE_CHILD_PROCESS.getAndSet(null);
        if (process != null) {
            destroyProcessTree(process.toHandle());
        }
    }

    private static void destroyProcessTree(ProcessHandle handle) {
        if (handle == null) {
            return;
        }
        handle.descendants()
                .sorted((left, right) -> Long.compare(right.pid(), left.pid()))
                .forEach(PotatoUpdater::destroyProcessHandle);
        destroyProcessHandle(handle);
    }

    private static void destroyProcessHandle(ProcessHandle handle) {
        if (handle == null || !handle.isAlive()) {
            return;
        }
        handle.destroy();
        try {
            handle.onExit().get(1500, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
        }
    }

    private static String getArgValue(String[] args, String key) {
        if (args == null) {
            return null;
        }
        for (int i = 0; i < args.length - 1; i++) {
            if (key.equalsIgnoreCase(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static long parseLongOrDefault(String value, long fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (flag.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }
}
