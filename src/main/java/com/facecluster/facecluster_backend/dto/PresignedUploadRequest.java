package com.facecluster.facecluster_backend.dto;

public record PresignedUploadRequest(
        String fileName,
        String contentType
) {
}