package com.easyapply.dto;

import lombok.Data;

@Data
public class ProgressUpdate {
    private int percent;
    private String message;
    private String status;

    public ProgressUpdate(int completed, int total, String message) {
        this.percent = total > 0 ? (int) (((double) completed / total) * 100) : 0;
        this.message = message;
        this.status = "processing";
    }
}