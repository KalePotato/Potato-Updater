package com.potato.updater.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * 哈希计算工具
 *
 * 【双需求】：
 * 1. 明确目标：提供针对文件的 SHA-256 签名计算，得到统一的 64 位小写 Hex 字符串。
 * 2. 系统目的：防止文件在下载传输过程中出现比特侧漏损坏；帮助判断现有静态资源是否需要更新。
 *
 * 【双逻辑】：
 * 1. 底层逻辑：流式读取大文件进行 MessageDigest Hash，避免 OOM。
 * 2. 配合逻辑：将该结果直接与远端 JSON 的 hash 字段进行 String.equals() 比较，决定后续下载任务构建。
 */
public class HashUtil {

    private static final String ALGORITHM = "SHA-256";

    public interface ProgressListener {
        void onProgress(long processedBytes, long totalBytes);
    }

    /**
     * 计算文件的 SHA-256 并返回十六进制全小写字符串
     */
    public static String calculateSHA256(Path filePath) {
        return calculateSHA256(filePath, null);
    }

    public static String calculateSHA256(Path filePath, ProgressListener progressListener) {
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            return null;
        }

        try (InputStream is = Files.newInputStream(filePath)) {
            long totalBytes = Files.size(filePath);
            long processedBytes = 0L;
            long lastReportAt = 0L;
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] buffer = new byte[262144];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Hash calculation interrupted");
                }
                digest.update(buffer, 0, bytesRead);
                processedBytes += bytesRead;

                if (progressListener != null) {
                    long now = System.currentTimeMillis();
                    if (processedBytes == totalBytes || now - lastReportAt >= 40L) {
                        progressListener.onProgress(processedBytes, totalBytes);
                        lastReportAt = now;
                    }
                }
            }
            return bytesToHex(digest.digest());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 字节数组转为 Hex 字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString().toLowerCase();
    }
}
