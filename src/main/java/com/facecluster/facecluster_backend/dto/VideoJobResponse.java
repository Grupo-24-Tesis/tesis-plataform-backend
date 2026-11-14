package com.facecluster.facecluster_backend.dto;

import java.time.Instant;

public record VideoJobResponse(
        String jobId,
        String originalFileName,
        String bucket,
        String objectKey,
        String cameraId,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String failureReason,
        Instant startedAt,
        Instant completedAt,
        Integer faceCount,
        Long queueWaitSeconds,
        Long processingSeconds
) {
}
