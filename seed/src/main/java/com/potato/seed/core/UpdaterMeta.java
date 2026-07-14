package com.potato.seed.core;

import com.google.gson.annotations.SerializedName;

/**
 * 远端 seed.json 的结构定义
 */
public class UpdaterMeta {

    @SerializedName("Potato_Updater")
    private String potatoUpdaterUrl; // 远端提供的更新器下载直链

    @SerializedName("enabled")
    private Boolean enabled; // 远端是否有禁用该 Seed 的后门

    @SerializedName("version")
    private String version; // 远端记录的版本号字符串

    // (可选) 可能有的 hash，如果远端配了则进行校验，没配就跳过
    @SerializedName("hash256")
    private String hash256;

    public String getPotatoUpdaterUrl() {
        return potatoUpdaterUrl;
    }

    public void setPotatoUpdaterUrl(String potatoUpdaterUrl) {
        this.potatoUpdaterUrl = potatoUpdaterUrl;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getHash256() {
        return hash256;
    }

    public void setHash256(String hash256) {
        this.hash256 = hash256;
    }
}
