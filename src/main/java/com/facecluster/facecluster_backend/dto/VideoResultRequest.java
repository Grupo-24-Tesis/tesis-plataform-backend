package com.facecluster.facecluster_backend.dto;

import java.util.List;

public record VideoResultRequest(
        List<FaceAppearanceResult> appearances
) {
    public record FaceAppearanceResult(
            String faceId,
            String timestamp,
            String frameUrl,
            String capturedAt,
            String embedding
    ) {
        public FaceAppearanceResult(
                String faceId,
                String timestamp,
                String frameUrl,
                String capturedAt
        ) {
            this(faceId, timestamp, frameUrl, capturedAt, null);
        }
    }
}
