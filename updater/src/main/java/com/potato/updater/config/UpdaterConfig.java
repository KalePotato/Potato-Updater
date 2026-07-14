package com.potato.updater.config;

/**
 * Updater 配置模型
 *
 * [SEVERE-01 / MEDIUM-05] 新增：Updater 的可配置化 URL 与选项，
 * 替代硬编码的 example 域名，允许运行时从配置文件读取。
 */
public class UpdaterConfig {

    // 是否启用 Logo
    private boolean enableLogo = true;

    // 是否启用远端路由 (storage.json)
    private boolean enableStorageTargetUrl = true;

    // 服务端存储路由地址 (storage.json)
    private String storageTargetUrl = "https://example.invalid/storage.json";

    // 自定义直达目标地址 (当 enableStorageTargetUrl=false 时生效)
    private String customTargetUrl = "";

    // 是否启用自动更新
    private boolean enabled = true;

    // Updater theme mode: system / light / dark
    private String themeMode = "system";

    // 用户是否已经明确保存过主题选择
    private boolean themeConfigured = false;

    public boolean isEnableLogo() {
        return enableLogo;
    }

    public void setEnableLogo(boolean enableLogo) {
        this.enableLogo = enableLogo;
    }

    public boolean isEnableStorageTargetUrl() {
        return enableStorageTargetUrl;
    }

    public void setEnableStorageTargetUrl(boolean enableStorageTargetUrl) {
        this.enableStorageTargetUrl = enableStorageTargetUrl;
    }

    public String getCustomTargetUrl() {
        return customTargetUrl;
    }

    public void setCustomTargetUrl(String customTargetUrl) {
        this.customTargetUrl = customTargetUrl;
    }

    public String getStorageTargetUrl() {
        return storageTargetUrl;
    }

    public void setStorageTargetUrl(String storageTargetUrl) {
        this.storageTargetUrl = storageTargetUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getThemeMode() {
        if (themeMode == null || themeMode.isBlank()) {
            return "system";
        }
        String normalized = themeMode.trim().toLowerCase();
        if ("bright".equals(normalized)) {
            return "light";
        }
        if ("dark_blurred".equals(normalized)
                || "dark-blurred".equals(normalized)
                || "darkblurred".equals(normalized)
                || "blurred".equals(normalized)) {
            return "dark";
        }
        if ("light".equals(normalized)
                || "dark".equals(normalized)
                || "system".equals(normalized)) {
            return normalized;
        }
        return "system";
    }

    public void setThemeMode(String themeMode) {
        this.themeMode = normalizeThemeMode(themeMode);
    }

    private String normalizeThemeMode(String themeMode) {
        if (themeMode == null || themeMode.isBlank()) {
            return "system";
        }
        String normalized = themeMode.trim().toLowerCase();
        if ("bright".equals(normalized)) {
            return "light";
        }
        if ("dark_blurred".equals(normalized)
                || "dark-blurred".equals(normalized)
                || "darkblurred".equals(normalized)
                || "blurred".equals(normalized)) {
            return "dark";
        }
        if ("light".equals(normalized)
                || "dark".equals(normalized)
                || "system".equals(normalized)) {
            return normalized;
        }
        return "system";
    }

    public boolean isThemeConfigured() {
        return themeConfigured;
    }

    public void setThemeConfigured(boolean themeConfigured) {
        this.themeConfigured = themeConfigured;
    }
}
