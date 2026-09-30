package com.facecluster.facecluster_backend.dto;

import java.time.Instant;

public record PresignedUploadResponse(
        String uploadUrl,
        String bucket,
        String objectKey,
        String originalFileName,
        String contentType,
        Instant expiresAt
) {
}