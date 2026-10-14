package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.dto.PresignedUploadRequest;
import com.facecluster.facecluster_backend.dto.PresignedUploadResponse;
import com.facecluster.facecluster_backend.dto.VideoJobResponse;
import com.facecluster.facecluster_backend.dto.VideoUploadedRequest;
import com.facecluster.facecluster_backend.service.S3UploadService;
import com.facecluster.facecluster_backend.service.VideoJobService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos")
@CrossOrigin(origins = "*")
public class VideoController {

    private final S3UploadService s3UploadService;
    private final VideoJobService videoJobService;

    public VideoController(
            S3UploadService s3UploadService,
            VideoJobService videoJobService
    ) {
        this.s3UploadService = s3UploadService;
        this.videoJobService = videoJobService;
    }

    @PostMapping("/upload-url")
    public PresignedUploadResponse createUploadUrl(
            @RequestBody PresignedUploadRequest request
    ) {
        return s3UploadService.createUploadUrl(request);
    }

    @PostMapping
    public VideoJobResponse registerUploadedVideo(
            @RequestBody VideoUploadedRequest request
    ) {
        return videoJobService.registerUploadedVideo(request);
    }
}