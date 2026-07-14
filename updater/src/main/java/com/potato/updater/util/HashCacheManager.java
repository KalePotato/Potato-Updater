package com.potato.updater.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 持久化缓存本地文件哈希，避免每次启动都全量重算。
 */
public class HashCacheManager {

    private static final Type CACHE_TYPE = new TypeToken<Map<String, HashRecord>>() { }.getType();

    private final Path cacheFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, HashRecord> cache = new HashMap<>();

    public HashCacheManager(PathResolver pathResolver) {
        Path cacheDir = pathResolver.getGameCoreDirectory().resolve("A_Potato_Updater").resolve("cache");
        this.cacheFile = cacheDir.resolve("hash_cache.json");
        try {
            Files.createDirectories(cacheDir);
        } catch (Exception ignored) {
        }
        load();
    }

    public String getIfFresh(String relativePath, Path filePath) {
        HashRecord record = cache.get(relativePath);
        if (record == null) {
            return null;
        }

        try {
            long size = Files.size(filePath);
            long lastModified = Files.getLastModifiedTime(filePath).toMillis();
            if (record.size == size && record.lastModified == lastModified) {
                return record.hash;
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    public void put(String relativePath, Path filePath, String hash) {
        if (hash == null || relativePath == null || relativePath.isEmpty()) {
            return;
        }

        try {
            long size = Files.size(filePath);
            long lastModified = Files.getLastModifiedTime(filePath).toMillis();
            cache.put(relativePath, new HashRecord(hash, size, lastModified));
        } catch (Exception ignored) {
        }
    }

    public void remove(String relativePath) {
        if (relativePath != null) {
            cache.remove(relativePath);
        }
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(cacheFile, StandardCharsets.UTF_8)) {
            gson.toJson(cache, CACHE_TYPE, writer);
        } catch (Exception ignored) {
        }
    }

    private void load() {
        if (!Files.exists(cacheFile)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
            Map<String, HashRecord> loaded = gson.fromJson(reader, CACHE_TYPE);
            if (loaded != null) {
                cache.clear();
                cache.putAll(loaded);
            }
        } catch (Exception ignored) {
        }
    }

    private static class HashRecord {
        private final String hash;
        private final long size;
        private final long lastModified;

        private HashRecord(String hash, long size, long lastModified) {
            this.hash = hash;
            this.size = size;
            this.lastModified = lastModified;
        }
    }
}
