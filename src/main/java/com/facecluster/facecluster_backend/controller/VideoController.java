package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.dto.PresignedUploadRequest;
import com.facecluster.facecluster_backend.dto.PresignedUploadResponse;
import com.facecluster.facecluster_backend.service.S3UploadService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final S3UploadService s3UploadService;

    public VideoController(S3UploadService s3UploadService) {
        this.s3UploadService = s3UploadService;
    }

    @PostMapping("/upload-url")
    public PresignedUploadResponse createUploadUrl(
            @RequestBody PresignedUploadRequest request
    ) {
        return s3UploadService.createUploadUrl(request);
    }
}