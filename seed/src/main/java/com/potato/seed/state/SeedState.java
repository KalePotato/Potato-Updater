package com.potato.seed.state;

/**
 * 记录本地 Updater 版本状态，用于和远端校验
 */
public class SeedState {

    // 当前本地已经成功安装的 Updater 版本号记录
    private String currentUpdaterVersion = "";
    private String currentSeedVersion = "";

    public String getCurrentUpdaterVersion() {
        return currentUpdaterVersion;
    }

    public void setCurrentUpdaterVersion(String currentUpdaterVersion) {
        this.currentUpdaterVersion = currentUpdaterVersion;
    }

    public String getCurrentSeedVersion() {
        return currentSeedVersion;
    }

    public void setCurrentSeedVersion(String currentSeedVersion) {
        this.currentSeedVersion = currentSeedVersion;
    }
}
