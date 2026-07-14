package com.potato.updater.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.potato.updater.util.PathResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ResourcePackOptionsManager {

    private static final String RESOURCE_PACKS_KEY = "resourcePacks";
    private static final String INCOMPATIBLE_RESOURCE_PACKS_KEY = "incompatibleResourcePacks";
    private static final List<String> DEFAULT_RESOURCE_PACKS = List.of("vanilla", "fabric");

    private final PathResolver pathResolver;
    private final Gson gson = new Gson();

    public ResourcePackOptionsManager(PathResolver pathResolver) {
        this.pathResolver = pathResolver;
    }

    public void applySelections(List<String> scopedPackFileNames,
                                List<String> selectedPackFileNames) throws IOException {
        if (scopedPackFileNames == null || scopedPackFileNames.isEmpty()) {
            return;
        }

        Path optionsPath = pathResolver.getGameCoreDirectory().resolve("options.txt");
        List<String> lines = readOptionsLines(optionsPath);
        List<String> currentEntries = parseOptionsListEntries(lines, RESOURCE_PACKS_KEY);
        List<String> currentIncompatibleEntries = parseOptionsListEntries(lines, INCOMPATIBLE_RESOURCE_PACKS_KEY);
        Integer expectedResourcePackFormat = resolveExpectedResourcePackFormat();

        List<String> baselineEntries = currentEntries.isEmpty()
                ? new ArrayList<>(DEFAULT_RESOURCE_PACKS)
                : new ArrayList<>(currentEntries);

        Set<String> scopedEntries = new LinkedHashSet<>();
        for (String fileName : scopedPackFileNames) {
            String entry = toResourcePackEntry(fileName);
            if (entry != null) {
                scopedEntries.add(entry);
            }
        }

        List<String> selectedEntries = new ArrayList<>();
        if (selectedPackFileNames != null) {
            for (String fileName : selectedPackFileNames) {
                String entry = toResourcePackEntry(fileName);
                if (entry != null && !selectedEntries.contains(entry)) {
                    selectedEntries.add(entry);
                }
            }
        }

        List<String> updatedEntries = mergeSelectedEntries(baselineEntries, scopedEntries, selectedEntries);
        List<String> updatedIncompatibleEntries = rebuildIncompatibleEntries(
                currentIncompatibleEntries,
                updatedEntries,
                scopedEntries,
                selectedEntries,
                expectedResourcePackFormat
        );

        upsertOptionsLine(lines, RESOURCE_PACKS_KEY, gson.toJson(updatedEntries));
        upsertOptionsLine(lines, INCOMPATIBLE_RESOURCE_PACKS_KEY, gson.toJson(updatedIncompatibleEntries));
        writeOptionsLinesAtomically(optionsPath, lines);
    }

    private List<String> mergeSelectedEntries(List<String> baselineEntries,
                                              Set<String> scopedEntries,
                                              List<String> selectedEntries) {
        List<String> updatedEntries = new ArrayList<>();
        for (String entry : baselineEntries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            if (scopedEntries.contains(entry)) {
                continue;
            }
            if (!updatedEntries.contains(entry)) {
                updatedEntries.add(entry);
            }
        }

        for (String entry : selectedEntries) {
            if (!updatedEntries.contains(entry)) {
                updatedEntries.add(entry);
            }
        }
        return updatedEntries;
    }

    private List<String> rebuildIncompatibleEntries(List<String> currentIncompatibleEntries,
                                                    List<String> updatedResourceEntries,
                                                    Set<String> scopedEntries,
                                                    List<String> selectedEntries,
                                                    Integer expectedResourcePackFormat) {
        List<String> updatedIncompatibleEntries = new ArrayList<>();
        for (String entry : currentIncompatibleEntries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            if (scopedEntries.contains(entry)) {
                continue;
            }
            if (!updatedResourceEntries.contains(entry)) {
                continue;
            }

            CompatibilityStatus status = determineCompatibility(entry, expectedResourcePackFormat);
            if (status == CompatibilityStatus.COMPATIBLE) {
                System.out.println("[ResourcePackOptions] Removed compatible pack from incompatible list: " + entry);
                continue;
            }
            if (!updatedIncompatibleEntries.contains(entry)) {
                updatedIncompatibleEntries.add(entry);
            }
        }

        for (String entry : selectedEntries) {
            CompatibilityStatus status = determineCompatibility(entry, expectedResourcePackFormat);
            if (status == CompatibilityStatus.INCOMPATIBLE) {
                if (!updatedIncompatibleEntries.contains(entry)) {
                    updatedIncompatibleEntries.add(entry);
                }
            } else if (status == CompatibilityStatus.UNKNOWN) {
                System.out.println("[ResourcePackOptions] Compatibility unknown, not writing incompatibleResourcePacks: " + entry);
            }
        }
        return updatedIncompatibleEntries;
    }

    private CompatibilityStatus determineCompatibility(String optionEntry, Integer expectedResourcePackFormat) {
        if (expectedResourcePackFormat == null) {
            return CompatibilityStatus.UNKNOWN;
        }

        Path packPath = resolveResourcePackPath(optionEntry);
        if (packPath == null) {
            return CompatibilityStatus.UNKNOWN;
        }

        try {
            JsonObject packMeta = readPackMeta(packPath);
            if (packMeta == null || !packMeta.has("pack") || !packMeta.get("pack").isJsonObject()) {
                return CompatibilityStatus.UNKNOWN;
            }

            JsonObject pack = packMeta.getAsJsonObject("pack");
            if (pack.has("supported_formats")) {
                Boolean supported = isSupportedBySupportedFormats(pack.get("supported_formats"), expectedResourcePackFormat);
                if (supported != null) {
                    return supported ? CompatibilityStatus.COMPATIBLE : CompatibilityStatus.INCOMPATIBLE;
                }
            }

            Integer packFormat = getInt(pack.get("pack_format"));
            if (packFormat == null) {
                return CompatibilityStatus.UNKNOWN;
            }
            return packFormat.equals(expectedResourcePackFormat)
                    ? CompatibilityStatus.COMPATIBLE
                    : CompatibilityStatus.INCOMPATIBLE;
        } catch (Exception e) {
            System.out.println("[ResourcePackOptions] Failed to inspect resource pack metadata: " + optionEntry
                    + " (" + e.getMessage() + ")");
            return CompatibilityStatus.UNKNOWN;
        }
    }

    private Path resolveResourcePackPath(String optionEntry) {
        if (optionEntry == null || !optionEntry.startsWith("file/")) {
            return null;
        }

        String fileName = optionEntry.substring("file/".length()).trim();
        if (fileName.isEmpty()) {
            return null;
        }

        Path resourcePacksDir = pathResolver.getGameCoreDirectory().resolve("resourcepacks").toAbsolutePath().normalize();
        Path packPath = resourcePacksDir.resolve(fileName).normalize();
        if (!packPath.startsWith(resourcePacksDir)) {
            return null;
        }
        return packPath;
    }

    private JsonObject readPackMeta(Path packPath) throws IOException {
        if (Files.isDirectory(packPath)) {
            Path metaPath = packPath.resolve("pack.mcmeta");
            if (!Files.isRegularFile(metaPath)) {
                return null;
            }
            try (InputStream input = Files.newInputStream(metaPath);
                 InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return gson.fromJson(reader, JsonObject.class);
            }
        }

        if (!Files.isRegularFile(packPath)) {
            return null;
        }

        try (ZipFile zipFile = new ZipFile(packPath.toFile())) {
            ZipEntry entry = zipFile.getEntry("pack.mcmeta");
            if (entry == null) {
                return null;
            }
            try (InputStream input = zipFile.getInputStream(entry);
                 InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return gson.fromJson(reader, JsonObject.class);
            }
        }
    }

    private Boolean isSupportedBySupportedFormats(JsonElement supportedFormats, int expectedResourcePackFormat) {
        if (supportedFormats == null || supportedFormats.isJsonNull()) {
            return null;
        }

        Integer exact = getInt(supportedFormats);
        if (exact != null) {
            return exact == expectedResourcePackFormat;
        }

        if (supportedFormats.isJsonArray()) {
            JsonArray array = supportedFormats.getAsJsonArray();
            if (array.size() == 2) {
                Integer min = getInt(array.get(0));
                Integer max = getInt(array.get(1));
                if (min != null && max != null) {
                    return expectedResourcePackFormat >= Math.min(min, max)
                            && expectedResourcePackFormat <= Math.max(min, max);
                }
            }

            for (JsonElement element : array) {
                Integer value = getInt(element);
                if (value != null && value == expectedResourcePackFormat) {
                    return true;
                }
            }
            return false;
        }

        if (supportedFormats.isJsonObject()) {
            JsonObject object = supportedFormats.getAsJsonObject();
            Integer min = getInt(object.get("min_inclusive"));
            Integer max = getInt(object.get("max_inclusive"));
            if (min == null) {
                min = getInt(object.get("min"));
            }
            if (max == null) {
                max = getInt(object.get("max"));
            }
            if (min != null && max != null) {
                return expectedResourcePackFormat >= min && expectedResourcePackFormat <= max;
            }
        }
        return null;
    }

    private Integer resolveExpectedResourcePackFormat() {
        Path gameCoreDirectory = pathResolver.getGameCoreDirectory();
        Set<String> relatedVersionNames = new LinkedHashSet<>();
        Set<String> relatedJarNames = new LinkedHashSet<>();

        String gameCoreName = fileNameOf(gameCoreDirectory);
        if (!gameCoreName.isBlank()) {
            relatedVersionNames.add(gameCoreName);
            relatedJarNames.add(gameCoreName);
        }

        for (Path versionJson : findVersionJsonCandidates(gameCoreDirectory)) {
            JsonObject root = readJsonObject(versionJson);
            if (root == null) {
                continue;
            }

            Integer format = extractPackVersionResource(root);
            if (format != null) {
                System.out.println("[ResourcePackOptions] Resolved expected resource pack format from "
                        + versionJson + ": " + format);
                return format;
            }

            addJsonString(root, "id", relatedVersionNames);
            addJsonString(root, "inheritsFrom", relatedVersionNames);
            addJsonString(root, "jar", relatedJarNames);
        }

        for (Path versionJar : findVersionJarCandidates(gameCoreDirectory, relatedVersionNames, relatedJarNames)) {
            Integer format = readPackVersionResourceFromJar(versionJar);
            if (format != null) {
                System.out.println("[ResourcePackOptions] Resolved expected resource pack format from "
                        + versionJar + ": " + format);
                return format;
            }
        }

        for (String versionName : relatedVersionNames) {
            Integer format = fallbackFormatFromVersionName(versionName);
            if (format != null) {
                System.out.println("[ResourcePackOptions] Resolved expected resource pack format from version name "
                        + versionName + ": " + format);
                return format;
            }
        }

        System.out.println("[ResourcePackOptions] Unable to resolve expected resource pack format.");
        return null;
    }

    private List<Path> findVersionJsonCandidates(Path gameCoreDirectory) {
        List<Path> candidates = new ArrayList<>();
        addPathIfRegularFile(candidates, gameCoreDirectory.resolve(fileNameOf(gameCoreDirectory) + ".json"));

        try {
            if (Files.isDirectory(gameCoreDirectory)) {
                try (var stream = Files.list(gameCoreDirectory)) {
                    stream.filter(path -> Files.isRegularFile(path)
                                    && fileNameOf(path).toLowerCase().endsWith(".json"))
                            .forEach(path -> addPathIfRegularFile(candidates, path));
                }
            }
        } catch (IOException ignored) {
        }
        return candidates;
    }

    private List<Path> findVersionJarCandidates(Path gameCoreDirectory,
                                                Set<String> relatedVersionNames,
                                                Set<String> relatedJarNames) {
        List<Path> candidates = new ArrayList<>();

        for (String jarName : relatedJarNames) {
            addPathIfRegularFile(candidates, gameCoreDirectory.resolve(jarName + ".jar"));
        }
        addPathIfRegularFile(candidates, gameCoreDirectory.resolve(fileNameOf(gameCoreDirectory) + ".jar"));

        try {
            if (Files.isDirectory(gameCoreDirectory)) {
                try (var stream = Files.list(gameCoreDirectory)) {
                    stream.filter(path -> Files.isRegularFile(path)
                                    && fileNameOf(path).toLowerCase().endsWith(".jar"))
                            .forEach(path -> addPathIfRegularFile(candidates, path));
                }
            }
        } catch (IOException ignored) {
        }

        Path versionsRoot = resolveVersionsRoot(gameCoreDirectory);
        if (versionsRoot != null) {
            for (String versionName : relatedVersionNames) {
                addPathIfRegularFile(candidates, versionsRoot.resolve(versionName).resolve(versionName + ".jar"));
                for (String jarName : relatedJarNames) {
                    addPathIfRegularFile(candidates, versionsRoot.resolve(versionName).resolve(jarName + ".jar"));
                }
            }
        }
        return candidates;
    }

    private Path resolveVersionsRoot(Path gameCoreDirectory) {
        Path parent = gameCoreDirectory.getParent();
        if (parent != null && "versions".equalsIgnoreCase(fileNameOf(parent))) {
            return parent;
        }

        Path current = gameCoreDirectory;
        for (int i = 0; i < 8 && current != null; i++) {
            if (".minecraft".equalsIgnoreCase(fileNameOf(current))) {
                return current.resolve("versions");
            }
            current = current.getParent();
        }

        Path launcherDirectory = pathResolver.getMinecraftUpperDirectory();
        if (launcherDirectory != null) {
            Path minecraftVersions = launcherDirectory.resolve(".minecraft").resolve("versions");
            if (Files.isDirectory(minecraftVersions)) {
                return minecraftVersions;
            }
        }
        return null;
    }

    private Integer readPackVersionResourceFromJar(Path jarPath) {
        try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
            ZipEntry entry = zipFile.getEntry("version.json");
            if (entry == null) {
                return null;
            }
            try (InputStream input = zipFile.getInputStream(entry);
                 InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                JsonObject root = gson.fromJson(reader, JsonObject.class);
                return extractPackVersionResource(root);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonObject readJsonObject(Path path) {
        try (InputStream input = Files.newInputStream(path);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, JsonObject.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer extractPackVersionResource(JsonObject root) {
        if (root == null || !root.has("pack_version") || !root.get("pack_version").isJsonObject()) {
            return null;
        }
        JsonObject packVersion = root.getAsJsonObject("pack_version");
        return getInt(packVersion.get("resource"));
    }

    private void addJsonString(JsonObject object, String key, Set<String> values) {
        if (object == null || !object.has(key)) {
            return;
        }
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return;
        }
        String value = element.getAsString();
        if (value != null && !value.isBlank()) {
            values.add(value.trim());
        }
    }

    private void addPathIfRegularFile(List<Path> paths, Path candidate) {
        if (candidate == null || !Files.isRegularFile(candidate)) {
            return;
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!paths.contains(normalized)) {
            paths.add(normalized);
        }
    }

    private Integer fallbackFormatFromVersionName(String versionName) {
        int[] version = parseSemanticVersion(versionName);
        if (version == null) {
            return null;
        }

        int major = version[0];
        int minor = version[1];
        int patch = version[2];
        if (major != 1) {
            return null;
        }
        if (minor >= 21) {
            if (minor == 21 && patch <= 1) {
                return 34;
            }
            return null;
        }
        if (minor == 20) {
            if (patch <= 1) {
                return 15;
            }
            if (patch == 2) {
                return 18;
            }
            if (patch <= 4) {
                return 22;
            }
            if (patch <= 6) {
                return 32;
            }
        }
        if (minor == 19) {
            if (patch >= 4) {
                return 13;
            }
            if (patch >= 3) {
                return 12;
            }
            return 9;
        }
        if (minor == 18) {
            return 8;
        }
        if (minor == 17) {
            return 7;
        }
        if (minor == 16) {
            return patch >= 2 ? 6 : 5;
        }
        if (minor == 15) {
            return 5;
        }
        if (minor == 14 || minor == 13) {
            return 4;
        }
        if (minor == 12 || minor == 11) {
            return 3;
        }
        if (minor == 10 || minor == 9) {
            return 2;
        }
        if (minor >= 6 && minor <= 8) {
            return 1;
        }
        return null;
    }

    private int[] parseSemanticVersion(String text) {
        if (text == null) {
            return null;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(^|[^0-9])1\\.(\\d+)(?:\\.(\\d+))?")
                .matcher(text);
        if (!matcher.find()) {
            return null;
        }

        int minor = Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
        return new int[]{1, minor, patch};
    }

    private Integer getInt(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsInt();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String fileNameOf(Path path) {
        Path fileName = path != null ? path.getFileName() : null;
        return fileName != null ? fileName.toString() : "";
    }

    private List<String> readOptionsLines(Path optionsPath) throws IOException {
        if (!Files.exists(optionsPath)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Files.readAllLines(optionsPath, StandardCharsets.UTF_8));
    }

    private List<String> parseOptionsListEntries(List<String> lines, String key) {
        if (lines == null || lines.isEmpty()) {
            return new ArrayList<>();
        }

        String rawValue = null;
        for (String line : lines) {
            if (line == null || !line.startsWith(key + ":")) {
                continue;
            }
            rawValue = line.substring((key + ":").length()).trim();
            break;
        }

        if (rawValue == null || rawValue.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            String[] entries = gson.fromJson(rawValue, String[].class);
            List<String> result = new ArrayList<>();
            if (entries != null) {
                for (String entry : entries) {
                    if (entry != null && !entry.isBlank() && !result.contains(entry)) {
                        result.add(entry);
                    }
                }
            }
            return result;
        } catch (JsonSyntaxException ignored) {
            return new ArrayList<>();
        }
    }

    private String toResourcePackEntry(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        return "file/" + fileName.trim();
    }

    private void upsertOptionsLine(List<String> lines, String key, String value) {
        String updatedLine = key + ":" + value;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line != null && line.startsWith(key + ":")) {
                lines.set(i, updatedLine);
                return;
            }
        }
        lines.add(updatedLine);
    }

    private void writeOptionsLinesAtomically(Path optionsPath, List<String> lines) throws IOException {
        Path parent = optionsPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempPath = optionsPath.resolveSibling(optionsPath.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
            for (int i = 0; i < lines.size(); i++) {
                writer.write(lines.get(i) == null ? "" : lines.get(i));
                if (i < lines.size() - 1) {
                    writer.write(System.lineSeparator());
                }
            }
        }

        try {
            Files.move(tempPath, optionsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailure) {
            Files.move(tempPath, optionsPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private enum CompatibilityStatus {
        COMPATIBLE,
        INCOMPATIBLE,
        UNKNOWN
    }
}
