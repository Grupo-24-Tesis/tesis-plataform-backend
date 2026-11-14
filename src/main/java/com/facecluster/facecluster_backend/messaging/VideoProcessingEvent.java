package com.facecluster.facecluster_backend.messaging;

import com.facecluster.facecluster_backend.dto.VideoResultRequest.FaceAppearanceResult;

import java.time.Instant;
import java.util.List;

public record VideoProcessingEvent(
        String eventType,
        String eventVersion,
        String jobId,
        Instant sentAt,
        String reason,
        List<FaceAppearanceResult> appearances
) {
    public static final String STARTED = "VIDEO_PROCESSING_STARTED";
    public static final String COMPLETED = "VIDEO_PROCESSING_COMPLETED";
    public static final String FAILED = "VIDEO_PROCESSING_FAILED";
}
