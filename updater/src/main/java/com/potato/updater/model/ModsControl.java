package com.potato.updater.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class ModsControl {

    @SerializedName("optionalMods")
    private List<String> optionalMods = new ArrayList<>();

    @SerializedName("mods")
    private List<ModEntry> mods = new ArrayList<>();

    public List<String> getOptionalMods() {
        return optionalMods != null ? optionalMods : List.of();
    }

    public void setOptionalMods(List<String> optionalMods) {
        this.optionalMods = optionalMods;
    }

    public List<ModEntry> getMods() {
        return mods != null ? mods : List.of();
    }

    public void setMods(List<ModEntry> mods) {
        this.mods = mods;
    }

    public static class ModEntry {
        @SerializedName("path")
        private String path;

        @SerializedName("name")
        private String name;

        @SerializedName("remark")
        private String remark;

        @SerializedName("placement")
        private String placement;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }

        public String getPlacement() {
            return placement;
        }

        public void setPlacement(String placement) {
            this.placement = placement;
        }
    }
}
