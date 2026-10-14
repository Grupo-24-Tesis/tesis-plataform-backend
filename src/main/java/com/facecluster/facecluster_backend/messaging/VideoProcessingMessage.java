package com.facecluster.facecluster_backend.messaging;

import java.time.Instant;

public record VideoProcessingMessage(
        String eventType,
        String eventVersion,
        String jobId,
        String bucket,
        String objectKey,
        String cameraId,
        String originalFileName,
        String contentType,
        Instant createdAt
) {
}