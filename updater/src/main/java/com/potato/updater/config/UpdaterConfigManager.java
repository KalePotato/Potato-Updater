package com.potato.updater.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * [SEVERE-01 / MEDIUM-05] 新增：Updater 配置自动创建与读取管理器。
 * 类似 SeedConfigManager，在 config/potato/updater_config.json 自动生成。
 */
public class UpdaterConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private UpdaterConfig currentConfig;
    private final Path configPath;

    public UpdaterConfigManager(Path gameCoreDir) {
        // 直接使用字符串字面量，避免常量缓存问题
        this.configPath = gameCoreDir.resolve("A_Potato_Updater").resolve("updater_config.json");
    }

    public void loadOrInitialize() throws IOException {
        if (!Files.exists(configPath)) {
            Files.createDirectories(configPath.getParent());
            this.currentConfig = new UpdaterConfig();
            save();
            System.out.println("[PotatoUpdater] Default config created at " + configPath.toAbsolutePath());
        } else {
            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                this.currentConfig = GSON.fromJson(reader, UpdaterConfig.class);
                System.out.println("[PotatoUpdater] Loaded config from " + configPath.toAbsolutePath());
            } catch (Exception e) {
                System.err.println("[PotatoUpdater] Failed to parse config, using fallback default.");
                this.currentConfig = new UpdaterConfig();
            }
        }
    }

    public void save() throws IOException {
        Files.createDirectories(configPath.getParent());
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            GSON.toJson(currentConfig, writer);
        }
    }

    public UpdaterConfig getConfig() {
        return currentConfig;
    }

    public void setConfig(UpdaterConfig config) {
        this.currentConfig = config;
    }
}
