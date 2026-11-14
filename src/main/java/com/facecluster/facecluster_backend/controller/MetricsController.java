package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.api.DateRangeParams;
import com.facecluster.facecluster_backend.dto.ActivityResponse;
import com.facecluster.facecluster_backend.dto.VideoJobMetrics;
import com.facecluster.facecluster_backend.service.ActivityService;
import com.facecluster.facecluster_backend.service.VideoJobMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final VideoJobMetricsService videoJobMetricsService;

    private final ActivityService activityService;

    public MetricsController(
            VideoJobMetricsService videoJobMetricsService,
            ActivityService activityService
    ) {
        this.videoJobMetricsService = videoJobMetricsService;
        this.activityService = activityService;
    }

    @GetMapping("/video-jobs")
    public VideoJobMetrics videoJobMetrics(
            @RequestParam(required = false) String cameraId
    ) {
        return videoJobMetricsService.calculate(cameraId);
    }

    @GetMapping("/activity")
    public ActivityResponse activity(
            @RequestParam(required = false) String cameraId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        return activityService.calculate(cameraId, DateRangeParams.parse(startDate, endDate));
    }
}
