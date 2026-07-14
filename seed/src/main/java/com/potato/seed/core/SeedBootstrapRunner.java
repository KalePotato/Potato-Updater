package com.potato.seed.core;

import com.potato.seed.config.SeedConfig;
import com.potato.seed.config.SeedConfigManager;
import com.potato.seed.state.SeedStateManager;
import com.potato.seed.ui.SeedGUI;
import com.potato.seed.util.SeedErrorLogger;

import java.nio.file.Path;

public class SeedBootstrapRunner {

    public int run(Path gameCorePath) {
        System.out.println("=================================================");
        System.out.println("Potato Seed Bootstrap Starting...");
        System.out.println("=================================================");
        System.out.println("[PotatoSeed] Detected Game Core Path: " + gameCorePath);

        SeedErrorLogger.initialize(gameCorePath);

        SeedConfigManager configManager = new SeedConfigManager(gameCorePath);
        try {
            configManager.loadOrInitialize();
        } catch (Exception e) {
            System.err.println("[PotatoSeed] Config error, disabling Seed to prevent crash.");
            SeedErrorLogger.logError("Failed to initialize or read Seed config", e);
            e.printStackTrace();
            return 1;
        }

        SeedConfig config = configManager.getConfig();
        if (!config.isEnableSeed()) {
            System.out.println("[PotatoSeed] enableSeed=false, bootstrap skipped.");
            return 0;
        }

        SeedStateManager stateManager = new SeedStateManager(gameCorePath);
        stateManager.loadOrInitialize();

        UpdaterFetcher fetcher = new UpdaterFetcher(config, stateManager, gameCorePath, null);
        try {
            fetcher.checkAndFetch();
        } catch (Exception ex) {
            String msg = ex.getMessage() != null ? ex.getMessage() : "update service error";
            SeedGUI errorGui = createErrorGui();
            boolean proceed = errorGui.askContinue(msg);
            if (!proceed) {
                errorGui.close();
                RuntimeException wrapped = new RuntimeException("User aborted after fetch failure: " + msg, ex);
                SeedErrorLogger.logError("User aborted launch due to fetcher failure.", wrapped);
                return 1;
            }
            errorGui.close();
        }

        try {
            UpdaterLauncher launcher = new UpdaterLauncher(config, gameCorePath, null);
            int updaterExitCode = launcher.launch(true);
            if (updaterExitCode != 0) {
                System.err.println("[PotatoSeed] Updater requested abort with exit code: " + updaterExitCode);
                return updaterExitCode;
            }
            System.out.println("[PotatoSeed] Potato_Updater.jar launched and completed.");
        } catch (Exception e) {
            System.err.println("[PotatoSeed] FATAL: Could not launch local Potato_Updater.jar.");
            SeedErrorLogger.logError("Failed to launch Potato Updater native process", e);
            e.printStackTrace();
            return 1;
        }

        System.out.println("Potato Seed Bootstrap Finished. Minecraft loading resumes.");
        return 0;
    }

    private SeedGUI createErrorGui() {
        System.setProperty("java.awt.headless", "false");

        SeedGUI gui = new SeedGUI();
        gui.show();
        gui.updateStatus("boot");
        gui.setCloseRequestHandler(() -> {
            gui.disposeSilently();
            System.exit(1);
        });
        return gui;
    }
}
