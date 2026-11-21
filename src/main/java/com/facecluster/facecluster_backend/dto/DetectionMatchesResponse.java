package com.facecluster.facecluster_backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DetectionMatchesResponse(
        String faceId,
        List<MatchLink> links,
        List<MatchDetectionResponse> detections
) {

    public record MatchLink(
            String id,
            String faceIdA,
            String faceIdB,
            String createdByGuardId,
            LocalDateTime createdAt
    ) {}
}
