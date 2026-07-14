package com.potato.updater.model;

import com.google.gson.annotations.SerializedName;

/**
 * 远端公告信息模型
 */
public class UpdateInfo {

    @SerializedName("mainTitle")
    private String mainTitle;

    @SerializedName("contentTitle")
    private String contentTitle;

    @SerializedName("body")
    private String body;

    public String getMainTitle() {
        return mainTitle != null ? mainTitle : "Potato 系统更新";
    }

    public String getContentTitle() {
        return contentTitle != null ? contentTitle : "发现新的增量内容";
    }

    public String getBody() {
        return body != null ? body : "暂无更新说明。";
    }
}
