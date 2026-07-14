package com.potato.updater.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathResolver {

    public static final String GAME_CORE_DIR_ROOT = "game_core_dir";
    public static final String LAUNCHER_DIR_ROOT = "launcher_dir";
    public static final String LEGACY_LUNCHER_DIR_ROOT = "luncher_dir";

    public static class PathContractException extends RuntimeException {
        public PathContractException(String message) {
            super(message);
        }
    }

    private Path gameCoreDirectory;
    private Path launcherDirectory;

    public void initialize() throws IOException {
        initializeFrom(Paths.get("").toAbsolutePath());
    }

    public void initializeFromGameCore(Path gameCorePath) throws IOException {
        Path candidate = normalizeExplicitGameCoreReference(gameCorePath);
        Path detectedCore = resolveExplicitGameCore(candidate);
        finishInitialization(detectedCore, gameCorePath);
    }

    public void initializeFrom(Path referencePath) throws IOException {
        Path candidate = normalizeReference(referencePath);
        Path detectedCore = detectGameCore(candidate);
        finishInitialization(detectedCore, referencePath);
    }

    private void finishInitialization(Path detectedCore, Path referencePath) throws IOException {
        if (detectedCore == null) {
            throw new IOException("Unable to resolve game core directory from: " + referencePath);
        }

        this.gameCoreDirectory = detectedCore.toAbsolutePath().normalize();
        this.launcherDirectory = detectLauncherDirectory(this.gameCoreDirectory);

        System.out.println("DEBUG: PathResolver initialized.");
        System.out.println(" - GameCore: " + this.gameCoreDirectory);
        System.out.println(" - Launcher: " + (this.launcherDirectory != null ? this.launcherDirectory : "NOT FOUND"));
    }

    private Path resolveExplicitGameCore(Path candidate) {
        Path normalizedCandidate = normalizeGameCoreCandidate(candidate);
        if (isAcceptedGameCoreDirectory(normalizedCandidate)) {
            return normalizedCandidate;
        }
        return null;
    }

    private Path detectGameCore(Path start) {
        Path current = start;
        for (int i = 0; i < 12 && current != null; i++) {
            Path candidate = normalizeGameCoreCandidate(current);
            if (isAcceptedGameCoreDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private Path normalizeExplicitGameCoreReference(Path referencePath) {
        Path candidate = toAbsolutePath(referencePath);
        if (candidate == null) {
            return null;
        }
        if (Files.isRegularFile(candidate)) {
            Path parent = candidate.getParent();
            if (parent != null && isWorkspaceSubDirectory(parent)) {
                return parent.getParent();
            }
            return candidate;
        }
        if (isWorkspaceSubDirectory(candidate)) {
            return candidate.getParent();
        }
        return candidate;
    }

    private Path normalizeReference(Path referencePath) {
        Path candidate = toAbsolutePath(referencePath);
        if (candidate == null) {
            return null;
        }
        if (Files.isRegularFile(candidate)) {
            candidate = candidate.getParent();
        }
        if (candidate != null && isWorkspaceSubDirectory(candidate)) {
            candidate = candidate.getParent();
        }
        return candidate;
    }

    private Path normalizeGameCoreCandidate(Path candidate) {
        if (candidate == null || !Files.isDirectory(candidate)) {
            return null;
        }

        Path working = candidate;
        if (isWorkspaceSubDirectory(working)) {
            working = working.getParent();
        }
        if (working == null) {
            return null;
        }

        if (working == null || !Files.isDirectory(working)) {
            return null;
        }

        return working;
    }

    private Path detectLauncherDirectory(Path gameCoreDir) {
        if (gameCoreDir == null) {
            return null;
        }

        String coreName = fileNameOf(gameCoreDir);
        if (".minecraft".equalsIgnoreCase(coreName)) {
            return gameCoreDir.getParent();
        }

        if (isVersionDirectoryUnderMinecraft(gameCoreDir)) {
            Path minecraftDir = gameCoreDir.getParent() != null ? gameCoreDir.getParent().getParent() : null;
            return minecraftDir != null ? minecraftDir.getParent() : null;
        }
        return null;
    }

    private boolean isWorkspaceSubDirectory(Path path) {
        String name = fileNameOf(path);
        return "A_Potato_Updater".equalsIgnoreCase(name)
                || "A_Potato_Seed".equalsIgnoreCase(name)
                || "mods".equalsIgnoreCase(name);
    }

    private boolean isAcceptedGameCoreDirectory(Path candidate) {
        // Keep the runtime contract narrow: only standard .minecraft layouts or
        // directories with explicit managed markers are treated as game cores.
        return candidate != null
                && (isMinecraftRoot(candidate)
                || isVersionDirectoryUnderMinecraft(candidate)
                || hasManagedMarkers(candidate));
    }

    private boolean isMinecraftRoot(Path path) {
        return ".minecraft".equalsIgnoreCase(fileNameOf(path));
    }

    private boolean isVersionDirectoryUnderMinecraft(Path path) {
        Path parent = path != null ? path.getParent() : null;
        if (parent == null || !"versions".equalsIgnoreCase(fileNameOf(parent))) {
            return false;
        }

        Path minecraftDir = parent.getParent();
        return minecraftDir != null && ".minecraft".equalsIgnoreCase(fileNameOf(minecraftDir));
    }

    private boolean hasManagedMarkers(Path path) {
        String directoryName = fileNameOf(path);
        if (directoryName.isEmpty()) {
            return false;
        }

        return Files.isRegularFile(path.resolve("Potato_Seed.jar"))
                || Files.isDirectory(path.resolve("A_Potato_Seed"))
                || Files.isDirectory(path.resolve("A_Potato_Updater"))
                || Files.isRegularFile(path.resolve(directoryName + ".json"))
                || Files.isRegularFile(path.resolve(directoryName + ".jar"));
    }

    private Path toAbsolutePath(Path referencePath) {
        if (referencePath == null) {
            return null;
        }
        return referencePath.toAbsolutePath().normalize();
    }

    private String fileNameOf(Path path) {
        Path name = path != null ? path.getFileName() : null;
        return name != null ? name.toString() : "";
    }

    public Path resolvePath(String pathStr) {
        if (gameCoreDirectory == null) {
            throw new PathContractException("PathResolver not initialized");
        }

        String normalized = normalizeVirtualPath(pathStr);

        Path targetBasePath;
        String actualRelPath;
        if (matchesVirtualRoot(normalized, GAME_CORE_DIR_ROOT)) {
            targetBasePath = gameCoreDirectory;
            actualRelPath = stripVirtualRoot(normalized, GAME_CORE_DIR_ROOT);
        } else if (matchesVirtualRoot(normalized, LAUNCHER_DIR_ROOT)
                || matchesVirtualRoot(normalized, LEGACY_LUNCHER_DIR_ROOT)) {
            if (launcherDirectory == null) {
                throw new PathContractException("launcher_dir is unavailable for path: " + pathStr);
            }
            targetBasePath = launcherDirectory;
            actualRelPath = stripVirtualRoot(normalized, detectLauncherVirtualRoot(normalized));
        } else {
            throw new PathContractException("Unknown path root: " + pathStr);
        }

        Path relativePath = validateRelativePath(actualRelPath, pathStr);
        Path normalizedBase = targetBasePath.toAbsolutePath().normalize();
        Path resolved = normalizedBase.resolve(relativePath).normalize();
        if (!resolved.startsWith(normalizedBase)) {
            throw new PathContractException("Resolved path escapes managed root: " + pathStr);
        }
        ensureExistingAncestorWithinBase(normalizedBase, resolved, pathStr);
        return resolved;
    }

    public String normalizeVirtualPath(String pathStr) {
        if (pathStr == null) {
            throw new PathContractException("Managed path is null");
        }
        String normalized = pathStr.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private Path validateRelativePath(String relativePath, String originalPath) {
        try {
            Path parsed = Paths.get(relativePath);
            if (parsed.isAbsolute()) {
                throw new PathContractException("Absolute managed path is not allowed: " + originalPath);
            }
            for (Path segment : parsed) {
                String value = segment.toString();
                if (".".equals(value) || "..".equals(value)) {
                    throw new PathContractException("Dot segments are not allowed in managed path: " + originalPath);
                }
            }
            return parsed;
        } catch (InvalidPathException e) {
            throw new PathContractException("Invalid managed path: " + originalPath);
        }
    }

    private void ensureExistingAncestorWithinBase(Path normalizedBase, Path resolved, String originalPath) {
        try {
            Path realBase = normalizedBase.toRealPath();
            Path existingAncestor = resolved;
            while (existingAncestor != null && !Files.exists(existingAncestor)) {
                existingAncestor = existingAncestor.getParent();
            }
            if (existingAncestor == null || !existingAncestor.toRealPath().startsWith(realBase)) {
                throw new PathContractException("Resolved path escapes managed root through a link: " + originalPath);
            }
        } catch (IOException e) {
            throw new PathContractException("Unable to validate managed path: " + originalPath);
        }
    }

    public boolean isGameCorePath(String pathStr) {
        return matchesVirtualRoot(normalizeVirtualPath(pathStr), GAME_CORE_DIR_ROOT);
    }

    public boolean isLauncherPath(String pathStr) {
        String normalized = normalizeVirtualPath(pathStr);
        return matchesVirtualRoot(normalized, LAUNCHER_DIR_ROOT)
                || matchesVirtualRoot(normalized, LEGACY_LUNCHER_DIR_ROOT);
    }

    public String stripKnownVirtualRoot(String pathStr) {
        String normalized = normalizeVirtualPath(pathStr);
        if (matchesVirtualRoot(normalized, GAME_CORE_DIR_ROOT)) {
            return stripVirtualRoot(normalized, GAME_CORE_DIR_ROOT);
        }
        if (isLauncherPath(normalized)) {
            return stripVirtualRoot(normalized, detectLauncherVirtualRoot(normalized));
        }
        return normalized;
    }

    private String detectLauncherVirtualRoot(String normalizedPath) {
        if (matchesVirtualRoot(normalizedPath, LAUNCHER_DIR_ROOT)) {
            return LAUNCHER_DIR_ROOT;
        }
        return LEGACY_LUNCHER_DIR_ROOT;
    }

    private boolean matchesVirtualRoot(String normalizedPath, String rootName) {
        return normalizedPath.equals(rootName) || normalizedPath.startsWith(rootName + "/");
    }

    private String stripVirtualRoot(String normalizedPath, String rootName) {
        if (normalizedPath.equals(rootName)) {
            return "";
        }
        return normalizedPath.substring((rootName + "/").length());
    }

    public Path getGameCoreDirectory() {
        return gameCoreDirectory;
    }

    public Path getMinecraftUpperDirectory() {
        return launcherDirectory;
    }
}
