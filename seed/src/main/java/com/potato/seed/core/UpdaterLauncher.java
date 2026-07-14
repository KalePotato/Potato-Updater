package com.potato.seed.core;

import com.potato.seed.config.SeedConfig;
import com.potato.seed.ui.SeedGUI;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class UpdaterLauncher {

    private final SeedConfig config;
    private final Path gameCorePath;
    private final SeedGUI gui;

    public UpdaterLauncher(SeedConfig config, Path gameCorePath, SeedGUI gui) {
        this.config = config;
        this.gameCorePath = gameCorePath;
        this.gui = gui;
    }

    public int launch() throws Exception {
        return launch(false);
    }

    public int launch(boolean prelaunchMode) throws Exception {
        Path updaterJar = gameCorePath
                .resolve(config.getUpdaterDirName())
                .resolve(config.getUpdaterJarName());
        Path updaterDir = gameCorePath.resolve(config.getUpdaterDirName());
        AtomicReference<Process> launchedProcess = new AtomicReference<>();

        if (!Files.exists(updaterJar)) {
            System.err.println("[PotatoSeed] Skipping Updater launch: " + updaterJar.toAbsolutePath() + " does not exist.");
            if (gui != null) {
                gui.updateStatus("no updater");
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            return 0;
        }

        String os = System.getProperty("os.name").toLowerCase();
        String javaExecutable = os.contains("win") ? "javaw.exe" : "java";
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + javaExecutable;
        if (!new File(javaBin).exists()) {
            javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java" + (os.contains("win") ? ".exe" : "");
        }

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-jar");
        command.add(updaterJar.toAbsolutePath().toString());
        command.add("--gameCoreDir");
        command.add(gameCorePath.toAbsolutePath().toString());
        if (prelaunchMode) {
            command.add("--prelaunch");
        }

        System.out.println("[PotatoSeed] Launching updater with blocking command: " + command);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(gameCorePath.toFile());
        Path logDir = updaterDir.resolve("updater_error_log");
        Files.createDirectories(logDir);
        String launchToken = Long.toString(System.currentTimeMillis());
        pb.redirectOutput(logDir.resolve("updater-" + launchToken + "-stdout.log").toFile());
        pb.redirectError(logDir.resolve("updater-" + launchToken + "-stderr.log").toFile());

        if (gui != null) {
            gui.updateStatus("launch updater");
            gui.setCloseRequestHandler(() -> {
                destroyProcessTree(launchedProcess.get());
                gui.disposeSilently();
                System.exit(1);
            });
        }

        Process process = pb.start();
        launchedProcess.set(process);

        if (gui != null) {
            gui.updateStatus("handoff");
            gui.close();
            try {
                Thread.sleep(800);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        int exitCode = process.waitFor();
        launchedProcess.compareAndSet(process, null);
        System.out.println("[PotatoSeed] Updater process finished with exit code: " + exitCode);
        return exitCode;
    }

    private void destroyProcessTree(Process process) {
        if (process == null) {
            return;
        }
        ProcessHandle handle = process.toHandle();
        handle.descendants()
                .sorted((left, right) -> Long.compare(right.pid(), left.pid()))
                .forEach(child -> destroyProcessHandle(child));
        destroyProcessHandle(handle);
    }

    private void destroyProcessHandle(ProcessHandle handle) {
        if (handle == null || !handle.isAlive()) {
            return;
        }
        handle.destroy();
        try {
            handle.onExit().get(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
        }
    }
}
