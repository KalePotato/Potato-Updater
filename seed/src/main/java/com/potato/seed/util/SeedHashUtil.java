package com.potato.seed.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * 极简哈希工具 (Seed 专供用)
 */
public class SeedHashUtil {

    public static String calculateSHA256(Path filePath) {
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            return null;
        }

        try (InputStream is = Files.newInputStream(filePath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            StringBuilder sb = new StringBuilder(2 * digest.getDigestLength());
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
