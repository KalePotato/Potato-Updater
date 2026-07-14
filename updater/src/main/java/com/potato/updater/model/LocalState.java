package com.potato.updater.model;

/**
 * 客户端本地状态模型 (state.json)
 * 只记录宏观版本，不记录具体文件。
 */
public class LocalState {

    // 上一次成功同步的服务端操作时间戳。用于跳过无变动更新。
    private String lastOperationTime = "";

    // 故障排查标记
    private boolean lastUpdateFailed = false;

    public String getLastOperationTime() {
        return lastOperationTime;
    }

    public void setLastOperationTime(String lastOperationTime) {
        this.lastOperationTime = lastOperationTime;
    }

    public boolean isLastUpdateFailed() {
        return lastUpdateFailed;
    }

    public void setLastUpdateFailed(boolean lastUpdateFailed) {
        this.lastUpdateFailed = lastUpdateFailed;
    }
}
