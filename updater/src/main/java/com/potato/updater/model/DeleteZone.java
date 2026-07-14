package com.potato.updater.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 删除区记录模型
 */
public class DeleteZone {

    public static class DeleteItem {
        private String path;
        private String reason;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    private List<DeleteItem> items = new ArrayList<>();

    public List<DeleteItem> getItems() {
        return items;
    }

    public void setItems(List<DeleteItem> items) {
        this.items = items;
    }
}
