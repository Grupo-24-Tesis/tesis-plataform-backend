package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.dto.VideoJobResponse;
import com.facecluster.facecluster_backend.service.JobFacesService;
import com.facecluster.facecluster_backend.service.VideoJobService;
import com.facecluster.facecluster_backend.dto.VideoResultRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/video-jobs")
public class VideoJobController {

    private final VideoJobService videoJobService;
    private final JobFacesService jobFacesService;

    public VideoJobController(
            VideoJobService videoJobService,
            JobFacesService jobFacesService
    ) {
        this.videoJobService = videoJobService;
        this.jobFacesService = jobFacesService;
    }

    @GetMapping
    public List<VideoJobResponse> listJobs(
            @RequestParam(required = false) String cameraId
    ) {
        return videoJobService.listJobs(cameraId);
    }

    @GetMapping("/{jobId}/faces")
    public List<FaceAppearance> getJobFaces(
            @PathVariable String jobId
    ) {
        return jobFacesService.getFaces(jobId);
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