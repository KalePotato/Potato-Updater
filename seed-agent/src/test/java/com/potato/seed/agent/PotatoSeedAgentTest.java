package com.potato.seed.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PotatoSeedAgentTest {

    @TempDir
    Path tempDir;

    @Test
    void deriveGameCoreDirAcceptsRootSeedJar() throws IOException {
        Path versionDir = Files.createDirectories(
                tempDir.resolve("launcher")
                        .resolve(".minecraft")
                        .resolve("versions")
                        .resolve("demo"));
        Files.createDirectories(versionDir.resolve("A_Potato_Seed"));
        Path seedJar = versionDir.resolve("Potato_Seed.jar");
        Files.writeString(seedJar, "seed");

        assertEquals(
                versionDir.toAbsolutePath().normalize(),
                PotatoSeedAgent.deriveGameCoreDirFromCodeSource(seedJar));
    }

    @Test
    void deriveGameCoreDirRejectsModsSeedJar() throws IOException {
        Path versionDir = createVersionDir("mods");
        Path seedJar = versionDir.resolve("mods").resolve("Potato_Seed.jar");
        Files.createDirectories(seedJar.getParent());
        Files.writeString(seedJar, "seed");

        assertThrows(IllegalStateException.class,
                () -> PotatoSeedAgent.deriveGameCoreDirFromCodeSource(seedJar));
    }

    @Test
    void deriveGameCoreDirRejectsSeedConfigDirectoryJar() throws IOException {
        Path versionDir = createVersionDir("seed");
        Path seedJar = versionDir.resolve("A_Potato_Seed").resolve("Potato_Seed.jar");
        Files.createDirectories(seedJar.getParent());
        Files.writeString(seedJar, "seed");

        assertThrows(IllegalStateException.class,
                () -> PotatoSeedAgent.deriveGameCoreDirFromCodeSource(seedJar));
    }

    @Test
    void deriveGameCoreDirRejectsUpdaterDirectoryJar() throws IOException {
        Path versionDir = createVersionDir("updater");
        Path seedJar = versionDir.resolve("A_Potato_Updater").resolve("Potato_Seed.jar");
        Files.createDirectories(seedJar.getParent());
        Files.writeString(seedJar, "seed");

        assertThrows(IllegalStateException.class,
                () -> PotatoSeedAgent.deriveGameCoreDirFromCodeSource(seedJar));
    }

    @Test
    void resolveGameCoreDirKeepsExplicitArgumentPriority() throws IOException {
        Path explicitCore = createVersionDir("explicit");
        Path seedJar = explicitCore.resolve("mods").resolve("Potato_Seed.jar");
        Files.createDirectories(seedJar.getParent());
        Files.writeString(seedJar, "seed");

        assertEquals(
                explicitCore.toAbsolutePath().normalize(),
                PotatoSeedAgent.resolveGameCoreDir(
                        "gameCoreDir=" + explicitCore,
                        seedJar,
                        tempDir));
    }

    @Test
    void resolveGameCoreDirFallsBackToWorkingDirectoryForDevelopmentClasses() throws IOException {
        Path classesDir = Files.createDirectories(tempDir.resolve("seed-agent").resolve("classes"));
        Path workingDirectory = createVersionDir("working");

        assertEquals(
                workingDirectory.toAbsolutePath().normalize(),
                PotatoSeedAgent.resolveGameCoreDir("", classesDir, workingDirectory));
    }

    private Path createVersionDir(String versionName) throws IOException {
        return Files.createDirectories(
                tempDir.resolve("launcher")
                        .resolve(".minecraft")
                        .resolve("versions")
                        .resolve(versionName));
    }
}
