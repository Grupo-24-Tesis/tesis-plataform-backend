package com.facecluster.facecluster_backend.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "face_matches")
public class FaceMatch {

    @Id
    private String id;

    @Indexed
    private String faceIdA;

    @Indexed
    private String faceIdB;

    private String decision;
    private Double score;
    private String model;
    private String createdByGuardId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public FaceMatch() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFaceIdA() { return faceIdA; }
    public void setFaceIdA(String faceIdA) { this.faceIdA = faceIdA; }

    public String getFaceIdB() { return faceIdB; }
    public void setFaceIdB(String faceIdB) { this.faceIdB = faceIdB; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getCreatedByGuardId() { return createdByGuardId; }
    public void setCreatedByGuardId(String createdByGuardId) { this.createdByGuardId = createdByGuardId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
