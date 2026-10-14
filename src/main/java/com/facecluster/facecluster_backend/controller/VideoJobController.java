package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.dto.VideoJobResponse;
import com.facecluster.facecluster_backend.service.VideoJobService;
import com.facecluster.facecluster_backend.dto.VideoResultRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/video-jobs")
@CrossOrigin(origins = "*")
public class VideoJobController {

    private final VideoJobService videoJobService;

    public VideoJobController(VideoJobService videoJobService) {
        this.videoJobService = videoJobService;
    }

    @GetMapping("/{jobId}")
    public VideoJobResponse getJob(
            @PathVariable String jobId
    ) {
        return videoJobService.getJobById(jobId);
    }

    @PostMapping("/{jobId}/results")
    public void receiveResults(
        @PathVariable String jobId,
        @RequestBody VideoResultRequest request
    )   {
    videoJobService.processResults(jobId, request);
    }
}