package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.dto.VideoJobMetrics;
import com.facecluster.facecluster_backend.service.VideoJobMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final VideoJobMetricsService videoJobMetricsService;

    public MetricsController(VideoJobMetricsService videoJobMetricsService) {
        this.videoJobMetricsService = videoJobMetricsService;
    }

    @GetMapping("/video-jobs")
    public VideoJobMetrics videoJobMetrics(
            @RequestParam(required = false) String cameraId
    ) {
        return videoJobMetricsService.calculate(cameraId);
    }
}
