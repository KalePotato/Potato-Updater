package com.potato.updater.model;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

/**
 * 【双需求】：
 * - 明确目标：抽象单个待管理文件在系统中的属性。
 * - 宏观层面的配合目的：通过 "路径类型 + 相对路径" 定位文件唯一身份，避免层级干扰。提供哈希值进行前后对比。
 *
 * [MEDIUM-01] 修复：补充 plan 第9章要求的 fileName, updateTime, category 字段
 */
public class FileEntry {
    @SerializedName("path")
    private String path;

    private String fileName; // [MEDIUM-01] plan 要求：文件名
    private String hash256;
    private Long sizeBytes;
    private String updateTime; // [MEDIUM-01] plan 要求：更新时间
    private boolean isRequired;
    private int fileVersion;
    private String category; // [MEDIUM-01] plan 要求：所属分类

    // Default constructor for Gson
    public FileEntry() {
    }

    public FileEntry(String path, String hash256) {
        this.path = path;
        this.hash256 = hash256;
        this.isRequired = true;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }


    public String getHash256() {
        return hash256;
    }

    public void setHash256(String hash256) {
        this.hash256 = hash256;
    }

    public long getSizeBytes() {
        return sizeBytes == null ? -1L : sizeBytes;
    }

    public boolean hasSizeBytes() {
        return sizeBytes != null;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = Math.max(0L, sizeBytes);
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public void setRequired(boolean required) {
        isRequired = required;
    }

    public int getFileVersion() {
        return fileVersion;
    }

    public void setFileVersion(int fileVersion) {
        this.fileVersion = fileVersion;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    // 以 path 作为业务主键
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        FileEntry fileEntry = (FileEntry) o;
        return Objects.equals(path, fileEntry.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }
}
