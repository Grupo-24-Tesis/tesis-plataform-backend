package com.facecluster.facecluster_backend.dto;

import java.time.LocalDateTime;

public class UniqueFace {

    private String faceId;
    private String imageUrl;
    private LocalDateTime capturedAt;
    private int reportCount;
    private boolean suspect;

    public UniqueFace() {}

    public UniqueFace(
            String faceId,
            String imageUrl,
            LocalDateTime capturedAt,
            int reportCount,
            boolean suspect
    ) {
        this.faceId = faceId;
        this.imageUrl = imageUrl;
        this.capturedAt = capturedAt;
        this.reportCount = reportCount;
        this.suspect = suspect;
    }

    public String getFaceId() {
        return faceId;
    }

    public void setFaceId(String faceId) {
        this.faceId = faceId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(LocalDateTime capturedAt) {
        this.capturedAt = capturedAt;
    }

    public int getReportCount() {
        return reportCount;
    }

    public void setReportCount(int reportCount) {
        this.reportCount = reportCount;
    }

    public boolean isSuspect() {
        return suspect;
    }

    public void setSuspect(boolean suspect) {
        this.suspect = suspect;
    }
}