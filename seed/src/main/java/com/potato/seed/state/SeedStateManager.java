package com.potato.seed.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地版本状态记录的读取与持久化
 */
public class SeedStateManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private SeedState currentState;
    private final Path statePath;

    public SeedStateManager(Path gameCoreDir) {
        this.statePath = gameCoreDir.resolve("A_Potato_Seed").resolve("seed_state.json");
    }

    public void loadOrInitialize() {
        if (!Files.exists(statePath)) {
            try {
                Files.createDirectories(statePath.getParent());
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.currentState = new SeedState();
        } else {
            try (Reader reader = Files.newBufferedReader(statePath, StandardCharsets.UTF_8)) {
                this.currentState = GSON.fromJson(reader, SeedState.class);
            } catch (Exception e) {
                System.err.println("[PotatoSeed] Failed to parse seed_state.json, creating new.");
                this.currentState = new SeedState();
            }
        }
    }

    public void save() throws IOException {
        try (Writer writer = Files.newBufferedWriter(statePath, StandardCharsets.UTF_8)) {
            GSON.toJson(currentState, writer);
        }
    }

    public SeedState getState() {
        return currentState;
    }
}
