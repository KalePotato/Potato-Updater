package com.potato.updater.core;

import com.potato.updater.model.DeleteZone;
import com.potato.updater.model.FileEntry;
import com.potato.updater.model.ServerManifest;
import com.potato.updater.util.HashCacheManager;
import com.potato.updater.util.HashUtil;
import com.potato.updater.util.PathResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 【双需求】：
 * - 核心底层逻辑：抛弃本地缓存清单的抽象对比。利用 PathResolver 寻找实体文件，
 * 对于 ServerManifest 下发的文件列表进行实时物理存在性和 SHA-256 哈希值验证。
 * - 系统配合：生成新增/修改队列与移除队列，交由外部流程询问用户或执行下载。
 *
 * [SEVERE-04] 修复：toMap 添加合并策略，防止重复主键导致 IllegalStateException
 */
public class DiffEngine {

    public interface ProgressListener {
        void onProgress(int current, int total, String currentPath, long processedBytes, long totalBytes);
    }

    public static class DiffResult {
        public List<FileEntry> toDownloadOrUpdate = new ArrayList<>();
        public List<DeleteZone.DeleteItem> toDelete = new ArrayList<>();
        public List<String> resourcePackOptionScope = new ArrayList<>();
        public List<String> resourcePacksToInstall = new ArrayList<>();
        public int totalDownloadFiles = 0; // 记录总计的覆盖动作规模用于反馈

        public boolean hasChanges() {
            return !toDownloadOrUpdate.isEmpty() || !toDelete.isEmpty();
        }

        public boolean hasResourcePackOptionScope() {
            return !resourcePackOptionScope.isEmpty();
        }
    }

    /**
     * 仅针对 JSON 层面的轻量比对流程（不触碰本地实体文件，实体文件交由校验环节负责）
     *
     * @param serverManifest 服务端当前最新的文件清单与内置删除区
     * @param pathResolver   用于推断删除文件及预下载文件是否真身存在的路径解决器
     */
    public DiffResult calculateDiff(
            ServerManifest serverManifest,
            PathResolver pathResolver) {
        return calculateDiff(serverManifest, pathResolver, null);
    }

    public DiffResult calculateDiff(
            ServerManifest serverManifest,
            PathResolver pathResolver,
            ProgressListener progressListener) {

        DiffResult result = new DiffResult();
        HashCacheManager hashCacheManager = new HashCacheManager(pathResolver);

        if (serverManifest == null || serverManifest.getFiles() == null) {
            return result; // 远端为空，无事可做
        }

        int totalFiles = serverManifest.getFiles().size();
        int currentIndex = 0;

        // 1. 遍历服务端最新文件清单 (对比真实物理 Hash)
        for (FileEntry serverEntry : serverManifest.getFiles()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new RuntimeException("Scan cancelled");
            }
            currentIndex++;
            final int currentFileIndex = currentIndex;
            if (progressListener != null) {
                progressListener.onProgress(currentFileIndex, totalFiles, serverEntry.getPath(), 0L, 0L);
            }
            try {
                Path targetReal = pathResolver.resolvePath(serverEntry.getPath());

                if (!Files.exists(targetReal)) {
                    hashCacheManager.remove(serverEntry.getPath());
                    result.toDownloadOrUpdate.add(serverEntry);
                    result.totalDownloadFiles++;
                } else {
                    String localHash = hashCacheManager.getIfFresh(serverEntry.getPath(), targetReal);
                    if (localHash == null) {
                        localHash = HashUtil.calculateSHA256(targetReal, (processedBytes, totalBytes) -> {
                            if (progressListener != null) {
                                progressListener.onProgress(currentFileIndex, totalFiles, serverEntry.getPath(), processedBytes, totalBytes);
                            }
                        });
                        if (localHash != null) {
                            hashCacheManager.put(serverEntry.getPath(), targetReal, localHash);
                        }
                    } else if (progressListener != null) {
                        long totalBytes = Files.size(targetReal);
                        progressListener.onProgress(currentFileIndex, totalFiles, serverEntry.getPath(), totalBytes, totalBytes);
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        throw new RuntimeException("Scan cancelled");
                    }
                    if (serverEntry.getHash256() != null && !serverEntry.getHash256().equalsIgnoreCase(localHash)) {
                        result.toDownloadOrUpdate.add(serverEntry);
                        result.totalDownloadFiles++;
                    }
                }
            } catch (PathResolver.PathContractException e) {
                throw e;
            } catch (Exception e) {
                if ("Scan cancelled".equals(e.getMessage())) {
                    throw new RuntimeException("Scan cancelled", e);
                }
                result.toDownloadOrUpdate.add(serverEntry);
                result.totalDownloadFiles++;
            }
        }

        // 2. 剥离服务端带来的原先独立 DeleteZone 对象，直接从清单层接管
        if (serverManifest.getDeleteZone() != null) {
            for (DeleteZone.DeleteItem item : serverManifest.getDeleteZone()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new RuntimeException("Scan cancelled");
                }
                try {
                    Path targetReal = pathResolver.resolvePath(item.getPath());
                    if (Files.exists(targetReal)) {
                        result.toDelete.add(item);
                    }
                } catch (PathResolver.PathContractException e) {
                    throw e;
                } catch (Exception e) {
                    if ("Scan cancelled".equals(e.getMessage())) {
                        throw new RuntimeException("Scan cancelled", e);
                    }
                }
            }
        }

        hashCacheManager.save();
        return result;
    }
}
