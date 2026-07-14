package com.potato.updater.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileSystems;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdaterGUIPathDisplayTest {

    @TempDir
    Path tempDir;

    @Test
    void composeDisplayPathHandlesFilesystemRoot() {
        Path root = FileSystems.getDefault().getRootDirectories().iterator().next();

        String result = UpdaterGUI.composeDisplayPath(root, "PCL/options.txt", "[missing]");

        String expectedBase = root.toAbsolutePath().normalize().toString().replace('\\', '/');
        String expected = expectedBase.endsWith("/")
                ? expectedBase + "PCL/options.txt"
                : expectedBase + "/PCL/options.txt";
        assertEquals(expected, result);
    }

    @Test
    void composeDisplayPathKeepsLeafNameForNamedDirectory() {
        Path launcherDir = tempDir.resolve("launcher-home");

        assertEquals("launcher-home/PCL/options.txt",
                UpdaterGUI.composeDisplayPath(launcherDir, "PCL/options.txt", "[missing]"));
    }

    @Test
    void composeDisplayPathFallsBackToUnavailableLabel() {
        assertEquals("[missing]/PCL/options.txt",
                UpdaterGUI.composeDisplayPath(null, "PCL/options.txt", "[missing]"));
        assertEquals("[missing]",
                UpdaterGUI.composeDisplayPath(null, "", "[missing]"));
    }
}
