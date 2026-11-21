package com.facecluster.facecluster_backend.dto;

import java.time.LocalDateTime;

public record MatchDetectionResponse(
        String faceId,
        String jobId,
        String cameraId,
        String cameraName,
        String videoName,
        String timestamp,
        LocalDateTime capturedAt,
        Double score,
        String frameUrl
) {}
