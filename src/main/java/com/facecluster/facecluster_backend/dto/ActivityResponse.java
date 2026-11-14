package com.facecluster.facecluster_backend.dto;

import java.util.List;

public record ActivityResponse(
        List<HourCount> hours,
        List<CameraCount> cameras,
        long total
) {

    public record HourCount(int hour, long count) {
    }

    public record CameraCount(String cameraId, String cameraName, long count) {
    }
}
