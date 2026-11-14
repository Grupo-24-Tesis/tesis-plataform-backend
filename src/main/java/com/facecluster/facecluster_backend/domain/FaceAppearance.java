package com.facecluster.facecluster_backend.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "face_appearances")
public class FaceAppearance {

    @Id
    private String id;

    private String faceId;

    @Indexed
    private String jobId;

    private String timestamp;
    @Indexed
    private String cameraId;
    private String frameUrl;

    @Indexed
    private LocalDateTime capturedAt;

    public FaceAppearance() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFaceId() { return faceId; }
    public void setFaceId(String faceId) { this.faceId = faceId; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getCameraId() { return cameraId; }
    public void setCameraId(String cameraId) { this.cameraId = cameraId; }

    public String getFrameUrl() { return frameUrl; }
    public void setFrameUrl(String frameUrl) { this.frameUrl = frameUrl; }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(LocalDateTime capturedAt) {
        this.capturedAt = capturedAt;
    }
}