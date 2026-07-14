package com.potato.updater.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PathResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void initializeFromGameCoreAcceptsVersionDirectoryWithoutModsWhenManagedMarkersExist() throws IOException {
        Path launcherDir = tempDir.resolve("launcher");
        Path versionDir = createVersionDirectory(launcherDir, "demo");
        Files.writeString(versionDir.resolve("Potato_Seed.jar"), "seed");
        Files.createDirectories(versionDir.resolve("A_Potato_Updater"));

        PathResolver resolver = new PathResolver();
        resolver.initializeFromGameCore(versionDir);

        assertEquals(versionDir.toAbsolutePath().normalize(), resolver.getGameCoreDirectory());
        assertEquals(launcherDir.toAbsolutePath().normalize(), resolver.getMinecraftUpperDirectory());
    }

    @Test
    void initializeFromGameCoreDoesNotClimbToMinecraftWhenParentHasMods() throws IOException {
        Path launcherDir = tempDir.resolve("launcher");
        Path minecraftDir = launcherDir.resolve(".minecraft");
        Path versionDir = createVersionDirectory(launcherDir, "demo");
        Files.createDirectories(minecraftDir.resolve("mods"));

        PathResolver resolver = new PathResolver();
        resolver.initializeFromGameCore(versionDir);

        assertEquals(versionDir.toAbsolutePath().normalize(), resolver.getGameCoreDirectory());
    }

    @Test
    void initializeFromUpdaterWorkspaceFallsBackToVersionCore() throws IOException {
        Path launcherDir = tempDir.resolve("launcher");
        Path versionDir = createVersionDirectory(launcherDir, "demo");
        Path updaterDir = Files.createDirectories(versionDir.resolve("A_Potato_Updater"));

        PathResolver resolver = new PathResolver();
        resolver.initializeFrom(updaterDir);

        assertEquals(versionDir.toAbsolutePath().normalize(), resolver.getGameCoreDirectory());
        assertEquals(launcherDir.toAbsolutePath().normalize(), resolver.getMinecraftUpperDirectory());
    }

    @Test
    void initializeFromGameCoreCollapsesExplicitUpdaterWorkspaceFile() throws IOException {
        Path launcherDir = tempDir.resolve("launcher");
        Path versionDir = createVersionDirectory(launcherDir, "demo");
        Path updaterJar = versionDir.resolve("A_Potato_Updater").resolve("Potato_Updater.jar");
        Files.createDirectories(updaterJar.getParent());
        Files.writeString(updaterJar, "updater");

        PathResolver resolver = new PathResolver();
        resolver.initializeFromGameCore(updaterJar);

        assertEquals(versionDir.toAbsolutePath().normalize(), resolver.getGameCoreDirectory());
        assertEquals(launcherDir.toAbsolutePath().normalize(), resolver.getMinecraftUpperDirectory());
    }

    @Test
    void initializeFromModsWorkspaceFallsBackToVersionCore() throws IOException {
        Path launcherDir = tempDir.resolve("launcher");
        Path versionDir = createVersionDirectory(launcherDir, "demo");
        Path modsDir = Files.createDirectories(versionDir.resolve("mods"));

        PathResolver resolver = new PathResolver();
        resolver.initializeFrom(modsDir);

        assertEquals(versionDir.toAbsolutePath().normalize(), resolver.getGameCoreDirectory());
        assertEquals(launcherDir.toAbsolutePath().normalize(), resolver.getMinecraftUpperDirectory());
    }

    @Test
    void resolvePathRejectsGameCoreTraversal() throws IOException {
        Path versionDir = createVersionDirectory(tempDir.resolve("launcher"), "demo");
        PathResolver resolver = new PathResolver();
        resolver.initializeFromGameCore(versionDir);

        assertThrows(PathResolver.PathContractException.class,
                () -> resolver.resolvePath("game_core_dir/../../outside.txt"));
        assertThrows(PathResolver.PathContractException.class,
                () -> resolver.resolvePath("game_core_dir/mods/../../outside.txt"));
    }

    @Test
    void resolvePathRejectsLauncherTraversal() throws IOException {
        Path versionDir = createVersionDirectory(tempDir.resolve("launcher"), "demo");
        PathResolver resolver = new PathResolver();
        resolver.initializeFromGameCore(versionDir);

        assertThrows(PathResolver.PathContractException.class,
                () -> resolver.resolvePath("launcher_dir/../outside.txt"));
    }

    @Test
    void resolvePathKeepsValidTargetsInsideManagedRoots() throws IOException {
        Path launcherDir = tempDir.resolve("launcher");
        Path versionDir = createVersionDirectory(launcherDir, "demo");
        PathResolver resolver = new PathResolver();
        resolver.initializeFromGameCore(versionDir);

        assertEquals(versionDir.resolve("mods/example.jar").toAbsolutePath().normalize(),
                resolver.resolvePath("game_core_dir/mods/example.jar"));
        assertEquals(launcherDir.resolve("PCL/Setup.ini").toAbsolutePath().normalize(),
                resolver.resolvePath("launcher_dir/PCL/Setup.ini"));
    }

    private Path createVersionDirectory(Path launcherDir, String versionName) throws IOException {
        return Files.createDirectories(
                launcherDir.resolve(".minecraft").resolve("versions").resolve(versionName));
    }
}
