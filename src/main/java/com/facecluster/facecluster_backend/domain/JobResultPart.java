package com.facecluster.facecluster_backend.domain;

import com.facecluster.facecluster_backend.dto.VideoResultRequest.FaceAppearanceResult;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "job_result_parts")
public class JobResultPart {

    @Id
    private String id;

    @Indexed
    private String jobId;

    private String attemptId;
    private int part;
    private int parts;
    private List<FaceAppearanceResult> appearances;
    private String embeddingModel;
    private Integer embeddingDimensions;
    private LocalDateTime receivedAt;

    public JobResultPart() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getAttemptId() { return attemptId; }
    public void setAttemptId(String attemptId) { this.attemptId = attemptId; }

    public int getPart() { return part; }
    public void setPart(int part) { this.part = part; }

    public int getParts() { return parts; }
    public void setParts(int parts) { this.parts = parts; }

    public List<FaceAppearanceResult> getAppearances() { return appearances; }
    public void setAppearances(List<FaceAppearanceResult> appearances) {
        this.appearances = appearances;
    }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Integer getEmbeddingDimensions() { return embeddingDimensions; }
    public void setEmbeddingDimensions(Integer embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
}
