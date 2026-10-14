package com.facecluster.facecluster_backend.dto;

public record VideoUploadedRequest(
        String originalFileName,
        String bucket,
        String objectKey,
        String cameraId
) {
}