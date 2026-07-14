package com.potato.updater.core;

import com.potato.updater.model.DeleteZone;
import com.potato.updater.model.FileEntry;
import com.potato.updater.model.ServerManifest;
import com.potato.updater.util.PathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiffEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void calculateDiffFailsForUnknownVirtualRootInFileEntries() throws IOException {
        PathResolver resolver = new PathResolver();
        resolver.initializeFromGameCore(createVersionDirectory(tempDir.resolve("launcher"), "demo"));

        ServerManifest manifest = new ServerManifest();
        manifest.getFiles().add(new FileEntry("unknown_root/file.txt", "hash"));

        assertThrows(PathResolver.PathContractException.class,
                () -> new DiffEngine().calculateDiff(manifest, resolver));
    }

    @Test
    void calculateDiffFailsForUnavailableLauncherDirInDeleteZone() throws IOException {
        Path customCore = Files.createDirectories(tempDir.resolve("custom-core"));
        Files.writeString(customCore.resolve("Potato_Seed.jar"), "seed");

        PathResolver resolver = new PathResolver();
        resolver.initializeFromGameCore(customCore);

        DeleteZone.DeleteItem deleteItem = new DeleteZone.DeleteItem();
        deleteItem.setPath("launcher_dir/options.txt");

        ServerManifest manifest = new ServerManifest();
        manifest.getDeleteZone().add(deleteItem);

        assertThrows(PathResolver.PathContractException.class,
                () -> new DiffEngine().calculateDiff(manifest, resolver));
    }

    @Test
    void calculateDiffKeepsMissingPhysicalFilesAsDownloads() throws IOException {
        Path versionDir = createVersionDirectory(tempDir.resolve("launcher"), "demo");

        PathResolver resolver = new PathResolver();
        resolver.initializeFromGameCore(versionDir);

        FileEntry entry = new FileEntry("game_core_dir/mods/missing.jar", "hash");
        ServerManifest manifest = new ServerManifest();
        manifest.getFiles().add(entry);

        DiffEngine.DiffResult result = new DiffEngine().calculateDiff(manifest, resolver);

        assertEquals(1, result.toDownloadOrUpdate.size());
        assertEquals(entry, result.toDownloadOrUpdate.get(0));
        assertEquals(1, result.totalDownloadFiles);
    }

    private Path createVersionDirectory(Path launcherDir, String versionName) throws IOException {
        return Files.createDirectories(
                launcherDir.resolve(".minecraft").resolve("versions").resolve(versionName));
    }
}
