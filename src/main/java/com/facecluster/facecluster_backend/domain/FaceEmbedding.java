package com.facecluster.facecluster_backend.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "face_embeddings")
public class FaceEmbedding {

    @Id
    private String id;

    @Indexed
    private String faceId;

    @Indexed
    private String jobId;

    private String cameraId;

    @Indexed
    private String model;

    private int dimensions;
    private byte[] vector;
    private LocalDateTime createdAt;

    public FaceEmbedding() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFaceId() { return faceId; }
    public void setFaceId(String faceId) { this.faceId = faceId; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getCameraId() { return cameraId; }
    public void setCameraId(String cameraId) { this.cameraId = cameraId; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getDimensions() { return dimensions; }
    public void setDimensions(int dimensions) { this.dimensions = dimensions; }

    public byte[] getVector() { return vector; }
    public void setVector(byte[] vector) { this.vector = vector; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
