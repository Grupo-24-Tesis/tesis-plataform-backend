package com.facecluster.facecluster_backend.dto;

public record VideoJobMetrics(
        long total,
        long completed,
        long failed,
        long inProgress,
        double failureRate,
        Double averageQueueWaitSeconds,
        Double averageProcessingSeconds,
        long totalFaces,
        Double averageFacesPerVideo
) {
}
