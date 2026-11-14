package com.facecluster.facecluster_backend.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.Instant;

@Document(collection = "video_jobs")
public class VideoJob {

    @Id
    private String id;

    private String originalFileName;
    private String bucket;
    private String objectKey;
    private String cameraId;
    private String status;

    private String failureReason;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastWorkerEventAt;
    private Instant startedAt;
    private Instant completedAt;
    private Integer faceCount;

    public VideoJob() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getCameraId() {
        return cameraId;
    }

    public void setCameraId(String cameraId) {
        this.cameraId = cameraId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getLastWorkerEventAt() {
        return lastWorkerEventAt;
    }

    public void setLastWorkerEventAt(Instant lastWorkerEventAt) {
        this.lastWorkerEventAt = lastWorkerEventAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getFaceCount() {
        return faceCount;
    }

    public void setFaceCount(Integer faceCount) {
        this.faceCount = faceCount;
    }

    public Long queueWaitSeconds() {
        return secondsBetween(createdAt, startedAt);
    }

    public Long processingSeconds() {
        return secondsBetween(startedAt, completedAt);
    }

    private static Long secondsBetween(Instant from, Instant to) {
        if (from == null || to == null) {
            return null;
        }

        return Duration.between(from, to).getSeconds();
    }
}
