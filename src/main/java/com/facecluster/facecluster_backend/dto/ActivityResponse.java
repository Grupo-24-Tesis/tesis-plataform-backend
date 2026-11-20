package com.facecluster.facecluster_backend.dto;

import java.time.Instant;
import java.util.List;

public record ActivityResponse(
        List<HourCount> hours,
        List<CameraCount> cameras,
        List<VideoCount> videos,
        FollowUp followUp,
        long total
) {

    public record HourCount(int hour, long count) {
    }

    public record CameraCount(String cameraId, String cameraName, long count) {
    }

    public record VideoCount(String jobId, String fileName, Instant uploadedAt, long count) {
    }

    public record FollowUp(long investigation, long resolved, long markedOnly, long none) {
    }
}
