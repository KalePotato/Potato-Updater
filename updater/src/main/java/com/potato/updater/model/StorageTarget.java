package com.potato.updater.model;

/**
 * 远端存储路由模型
 * 用于解析 storage.json 获取实际的 target 下载基址。
 */
public class StorageTarget {
    private String target;

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }
}
