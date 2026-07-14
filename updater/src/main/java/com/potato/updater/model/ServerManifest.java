package com.potato.updater.model;

import java.util.ArrayList;
import java.util.List;
import com.potato.updater.model.FileEntry;
import com.potato.updater.model.DeleteZone;
import com.google.gson.annotations.SerializedName;

/**
 * 服务端/本地 核心更新清单模型表
 */
public class ServerManifest {

    @SerializedName("updateStateVersion")
    private int updateStateVersion; // Will be phased out in logic but kept for backward json parsability

    @SerializedName("operationTime")
    private String operationTime; // 新版核心人工基准时间

    @SerializedName("files")
    private List<FileEntry> files;

    @SerializedName("deleteZone")
    private List<DeleteZone.DeleteItem> deleteZone; // 合并：列表内直接包裹原删除项

    public ServerManifest() {
        this.files = new ArrayList<>();
        this.deleteZone = new ArrayList<>();
    }

    public int getUpdateStateVersion() {
        return updateStateVersion;
    }

    public void setUpdateStateVersion(int updateStateVersion) {
        this.updateStateVersion = updateStateVersion;
    }

    public List<FileEntry> getFiles() {
        return files;
    }

    public void setFiles(List<FileEntry> files) {
        this.files = files;
    }

    public String getOperationTime() {
        return operationTime;
    }

    public void setOperationTime(String operationTime) {
        this.operationTime = operationTime;
    }

    public List<DeleteZone.DeleteItem> getDeleteZone() {
        return deleteZone;
    }

    public void setDeleteZone(List<DeleteZone.DeleteItem> deleteZone) {
        this.deleteZone = deleteZone;
    }
}
