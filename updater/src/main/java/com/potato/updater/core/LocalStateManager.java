package com.potato.updater.core;

import com.google.gson.Gson;
import com.potato.updater.model.LocalState;
import com.potato.updater.util.PathResolver;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 【双需求】：
 * - 功能目标：管理 potato_version.json (宏观时间倒影)。
 * - 系统目的：利用"原子性提交"确保中途断电不会导致版本号提前变更，保障下一次安全重试。
 *
 * [SEVERE-02] 修复：新增 saveFailureState() 方法，区分正常提交和故障标记保存，
 * 确保 commitTransaction 中 lastUpdateFailed=false 不会覆盖故障标记。
 */
public class LocalStateManager {

    private final Path stateFile;
    private final Path hashCacheFile;
    private final Gson gson;

    private LocalState currentState;

    public LocalStateManager(PathResolver pathResolver, Gson gson) {
        this.gson = gson;
        // 直接使用字符串字面量，避免常量缓存导致实际值枋旧
        Path updaterDir = pathResolver.getGameCoreDirectory().resolve("A_Potato_Updater");
        try {
            Files.createDirectories(updaterDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.stateFile = updaterDir.resolve("potato_version.json");
        this.hashCacheFile = updaterDir.resolve("cache").resolve("hash_cache.json");
    }

    public void load() {
        // Load State
        if (Files.exists(stateFile)) {
            try (Reader reader = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
                currentState = gson.fromJson(reader, LocalState.class);
            } catch (Exception e) {
                System.err.println("解析 potato_version.json 失败，回退到全新状态。");
                currentState = new LocalState();
            }
        } else {
            currentState = new LocalState();
        }
    }

    /**
     * 【正常事务提交】：
     * 当所有文件安全落盘、旧文件被清理后，作为最后一步调用此方法提交事务。
     * 会将 lastUpdateFailed 置为 false。
     */
    public void commitTransaction(String operationTime) throws IOException {
        currentState.setLastOperationTime(operationTime);
        currentState.setLastUpdateFailed(false);
        writeState();
    }

    /**
     * [SEVERE-02] 新增：【故障标记保存】
     * 当任务执行失败时，只将 lastUpdateFailed=true 持久化，不修改版本号和 manifest。
     * 下一次启动时系统会再次尝试更新。
     */
    public void saveFailureState() throws IOException {
        currentState.setLastUpdateFailed(true);
        writeState();
    }

    public void resetState() throws IOException {
        Files.deleteIfExists(stateFile);
        Files.deleteIfExists(hashCacheFile);
        currentState = new LocalState();
    }

    private void writeState() throws IOException {
        try (Writer writer = Files.newBufferedWriter(stateFile, StandardCharsets.UTF_8)) {
            gson.toJson(currentState, writer);
        }
    }

    public LocalState getCurrentState() {
        return currentState;
    }
}
