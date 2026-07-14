package com.potato.seed.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 【双需求】：
 * - 功能目标：反序列化本地 config，决定引导动作。
 * - 系统目的：Minecraft 启动时自动在指定位置生成配置。新要求位于 Potato_Seed_Config/seed_config.json
 */
public class SeedConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private SeedConfig currentConfig;
    private final Path configPath;

    public SeedConfigManager(Path gameCoreDir) {
        // 新需求：位于 A_Potato_Seed 下，与 Updater 隔离
        this.configPath = gameCoreDir.resolve("A_Potato_Seed").resolve("seed_config.json");
    }

    public void loadOrInitialize() throws IOException {
        if (!Files.exists(configPath)) {
            Files.createDirectories(configPath.getParent());
            this.currentConfig = new SeedConfig();
            save();
            System.out.println("[PotatoSeed] Initialization: Default config created at " + configPath.toAbsolutePath());
        } else {
            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                this.currentConfig = GSON.fromJson(reader, SeedConfig.class);
                System.out.println("[PotatoSeed] Loaded config from " + configPath.toAbsolutePath());
            } catch (Exception e) {
                System.err.println("[PotatoSeed] Failed to parse config, using fallback default.");
                this.currentConfig = new SeedConfig();
            }
        }
    }

    private void save() throws IOException {
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            GSON.toJson(currentConfig, writer);
        }
    }

    public SeedConfig getConfig() {
        return currentConfig;
    }
}
