package com.facecluster.facecluster_backend.dto;

public record FaceMatchRequest(
        String faceIdA,
        String faceIdB,
        String decision,
        Double score,
        String createdByGuardId
) {}
