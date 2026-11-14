package com.facecluster.facecluster_backend.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "marked")
public class MarkedPerson {
    @Id
    private String id;
    private String faceId;
    private String reason;
    private String createdByGuardId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFaceId() { return faceId; }
    public void setFaceId(String faceId) { this.faceId = faceId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getCreatedByGuardId() { return createdByGuardId; }
    public void setCreatedByGuardId(String createdByGuardId) { this.createdByGuardId = createdByGuardId; }
}