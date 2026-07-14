package com.potato.seed.agent;

import com.potato.seed.core.SeedBootstrapRunner;

import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PotatoSeedAgent {

    private static final String RAN_FLAG = "potato.seed.agent.ran";
    private static final String SEED_JAR_NAME = "Potato_Seed.jar";

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        run(agentArgs);
    }

    public static void main(String[] args) {
        String agentArgs = args != null && args.length > 0 ? args[0] : "";
        run(agentArgs);
    }

    private static synchronized void run(String agentArgs) {
        if (Boolean.getBoolean(RAN_FLAG)) {
            System.out.println("[PotatoSeedAgent] Bootstrap already ran in this JVM, skipping duplicate.");
            return;
        }
        System.setProperty(RAN_FLAG, "true");

        final Path gameCoreDir;
        try {
            gameCoreDir = resolveGameCoreDir(agentArgs);
        } catch (IllegalStateException e) {
            System.err.println("[PotatoSeedAgent] " + e.getMessage());
            System.exit(1);
            return;
        }

        int exitCode = new SeedBootstrapRunner().run(gameCoreDir);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static Path resolveGameCoreDir(String agentArgs) {
        String explicit = extractNamedArg(agentArgs, "gameCoreDir");
        if (explicit != null && !explicit.isBlank()) {
            return Paths.get(explicit).toAbsolutePath().normalize();
        }

        Path workingDirectory = Paths.get("").toAbsolutePath().normalize();
        try {
            Path agentJar = Paths.get(PotatoSeedAgent.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()).toAbsolutePath().normalize();
            return resolveGameCoreDir(agentArgs, agentJar, workingDirectory);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception ignored) {
        }

        return workingDirectory;
    }

    static Path resolveGameCoreDir(String agentArgs, Path codeSourceLocation, Path workingDirectory) {
        String explicit = extractNamedArg(agentArgs, "gameCoreDir");
        if (explicit != null && !explicit.isBlank()) {
            return Paths.get(explicit).toAbsolutePath().normalize();
        }

        if (Files.isRegularFile(codeSourceLocation)) {
            return deriveGameCoreDirFromCodeSource(codeSourceLocation);
        }

        return workingDirectory != null ? workingDirectory.toAbsolutePath().normalize() : null;
    }

    static Path deriveGameCoreDirFromCodeSource(Path codeSourceLocation) {
        if (codeSourceLocation == null) {
            return null;
        }

        Path seedJarPath = codeSourceLocation.toAbsolutePath().normalize();
        if (!Files.isRegularFile(seedJarPath)) {
            return seedJarPath;
        }

        if (!SEED_JAR_NAME.equalsIgnoreCase(fileNameOf(seedJarPath))) {
            throw unsupportedLayout(seedJarPath);
        }

        Path candidate = seedJarPath.getParent();
        if (!isSupportedGameCoreDirectory(candidate)) {
            throw unsupportedLayout(seedJarPath);
        }
        return candidate.toAbsolutePath().normalize();
    }

    private static String extractNamedArg(String agentArgs, String key) {
        if (agentArgs == null || agentArgs.isBlank()) {
            return null;
        }

        String[] parts = agentArgs.split("[;,]");
        for (String part : parts) {
            String trimmed = part.trim();
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String name = trimmed.substring(0, separator).trim();
            if (key.equalsIgnoreCase(name)) {
                return trimmed.substring(separator + 1).trim();
            }
        }
        return null;
    }

    private static IllegalStateException unsupportedLayout(Path seedJarPath) {
        return new IllegalStateException(
                "Unsupported Potato_Seed.jar layout. Expected <gameCoreDir>/" + SEED_JAR_NAME
                        + " but found: " + seedJarPath);
    }

    private static boolean isSupportedGameCoreDirectory(Path path) {
        return path != null
                && Files.isDirectory(path)
                && (isMinecraftRoot(path)
                || isVersionDirectoryUnderMinecraft(path)
                || hasManagedMarkers(path));
    }

    private static boolean isMinecraftRoot(Path path) {
        return ".minecraft".equalsIgnoreCase(fileNameOf(path));
    }

    private static boolean isVersionDirectoryUnderMinecraft(Path path) {
        Path parent = path != null ? path.getParent() : null;
        if (parent == null || !"versions".equalsIgnoreCase(fileNameOf(parent))) {
            return false;
        }

        Path minecraftDir = parent.getParent();
        return minecraftDir != null && ".minecraft".equalsIgnoreCase(fileNameOf(minecraftDir));
    }

    private static boolean hasManagedMarkers(Path path) {
        String directoryName = fileNameOf(path);
        if (directoryName.isEmpty()) {
            return false;
        }

        return Files.isDirectory(path.resolve("A_Potato_Seed"))
                || Files.isDirectory(path.resolve("A_Potato_Updater"))
                || Files.isRegularFile(path.resolve(directoryName + ".json"))
                || Files.isRegularFile(path.resolve(directoryName + ".jar"));
    }

    private static String fileNameOf(Path path) {
        if (path == null || path.getFileName() == null) {
            return "";
        }
        return path.getFileName().toString();
    }
}
