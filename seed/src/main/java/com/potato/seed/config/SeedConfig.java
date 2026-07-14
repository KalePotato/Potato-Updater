package com.potato.seed.config;

/**
 * 【双需求】：
 * - 静态配置，提供对 Seed 本身行为的基本开关及远端地址设置。
 * - 新需求：
 * 1. enableSeed: 是否启用 seed
 * 2. enableUpdaterCheck: 是否到远端检查 updater 更新
 * 3. remoteConfigUrl: 远端 seed.json 地址
 */
public class SeedConfig {

    private boolean enableSeed = true;
    private boolean enableUpdaterCheck = true;
    private String remoteConfigUrl = "https://example.invalid/seed.json";

    // 内部存放 Updater 的目录名和 jar 名
    private String updaterDirName = "A_Potato_Updater";
    private String updaterJarName = "Potato_Updater.jar";

    public boolean isEnableSeed() {
        return enableSeed;
    }

    public void setEnableSeed(boolean enableSeed) {
        this.enableSeed = enableSeed;
    }

    public boolean isEnableUpdaterCheck() {
        return enableUpdaterCheck;
    }

    public void setEnableUpdaterCheck(boolean enableUpdaterCheck) {
        this.enableUpdaterCheck = enableUpdaterCheck;
    }

    public String getRemoteConfigUrl() {
        return remoteConfigUrl;
    }

    public void setRemoteConfigUrl(String remoteConfigUrl) {
        this.remoteConfigUrl = remoteConfigUrl;
    }

    public String getUpdaterDirName() {
        return updaterDirName;
    }

    public void setUpdaterDirName(String updaterDirName) {
        this.updaterDirName = updaterDirName;
    }

    public String getUpdaterJarName() {
        return updaterJarName;
    }

    public void setUpdaterJarName(String updaterJarName) {
        this.updaterJarName = updaterJarName;
    }
}
