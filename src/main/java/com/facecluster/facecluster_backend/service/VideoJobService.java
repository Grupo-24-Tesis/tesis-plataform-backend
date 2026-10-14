package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.VideoJob;
import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.dto.VideoResultRequest;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.dto.VideoJobResponse;
import com.facecluster.facecluster_backend.dto.VideoUploadedRequest;
import com.facecluster.facecluster_backend.repository.VideoJobRepository;
import com.facecluster.facecluster_backend.messaging.SqsVideoJobPublisher;
import com.facecluster.facecluster_backend.messaging.VideoProcessingMessage;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class VideoJobService {

    private final VideoJobRepository videoJobRepository;
    private final FaceAppearanceRepository faceAppearanceRepository;
    private final SqsVideoJobPublisher sqsVideoJobPublisher;

    public VideoJobService(VideoJobRepository videoJobRepository,FaceAppearanceRepository faceAppearanceRepository, SqsVideoJobPublisher sqsVideoJobPublisher) {  
    this.videoJobRepository = videoJobRepository;
    this.faceAppearanceRepository = faceAppearanceRepository;
    this.sqsVideoJobPublisher = sqsVideoJobPublisher;
    }

    public VideoJobResponse registerUploadedVideo(
        VideoUploadedRequest request
) {
    validateRequest(request);

    Instant now = Instant.now();

    VideoJob job = new VideoJob();
    job.setOriginalFileName(request.originalFileName().trim());
    job.setBucket(request.bucket().trim());
    job.setObjectKey(request.objectKey().trim());
    job.setCameraId(request.cameraId().trim());
    job.setStatus("UPLOADED");
    job.setCreatedAt(now);
    job.setUpdatedAt(now);

    VideoJob savedJob = videoJobRepository.save(job);

    VideoProcessingMessage message =
            new VideoProcessingMessage(
                    "VIDEO_UPLOAD_READY",
                    "1",
                    savedJob.getId(),
                    savedJob.getBucket(),
                    savedJob.getObjectKey(),
                    savedJob.getCameraId(),
                    savedJob.getOriginalFileName(),
                    detectContentType(
                            savedJob.getOriginalFileName()
                    ),
                    savedJob.getCreatedAt()
            );

    try {
        sqsVideoJobPublisher.publish(message);

        savedJob.setStatus("QUEUED");
        savedJob.setUpdatedAt(Instant.now());

        savedJob = videoJobRepository.save(savedJob);

    } catch (RuntimeException exception) {
        savedJob.setStatus("QUEUE_ERROR");
        savedJob.setUpdatedAt(Instant.now());

        videoJobRepository.save(savedJob);

        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "El video se guardó, pero no pudo enviarse a la cola SQS",
                exception
        );
    }

    return toResponse(savedJob);
}

    private void validateRequest(VideoUploadedRequest request) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La petición es obligatoria"
            );
        }

        if (isBlank(request.originalFileName())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "originalFileName es obligatorio"
            );
        }

        if (isBlank(request.bucket())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "bucket es obligatorio"
            );
        }

        if (isBlank(request.objectKey())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "objectKey es obligatorio"
            );
        }

        if (isBlank(request.cameraId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cameraId es obligatorio"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public VideoJobResponse getJobById(String jobId) {
    VideoJob job = videoJobRepository.findById(jobId)
            .orElseThrow(() ->
                    new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Job no encontrado"
                    )
            );

    return new VideoJobResponse(
            job.getId(),
            job.getOriginalFileName(),
            job.getBucket(),
            job.getObjectKey(),
            job.getCameraId(),
            job.getStatus(),
            job.getCreatedAt()
    );

    
    }

    public void processResults(
        String jobId,
        VideoResultRequest request
) {
    VideoJob job = videoJobRepository.findById(jobId)
            .orElseThrow(() ->
                    new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Job no encontrado"
                    )
            );

    if (request == null || request.appearances() == null) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "La lista de apariciones es obligatoria"
        );
    }

    List<FaceAppearance> appearances = new ArrayList<>();

    for (VideoResultRequest.FaceAppearanceResult result
            : request.appearances()) {

        if (result.faceId() == null ||
                result.faceId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "faceId es obligatorio"
            );
        }

        FaceAppearance appearance = new FaceAppearance();

        appearance.setFaceId(result.faceId());
        appearance.setTimestamp(result.timestamp());
        appearance.setFrameUrl(result.frameUrl());

        // El cameraId viene del VideoJob, no del modelo.
        appearance.setCameraId(job.getCameraId());

        if (result.capturedAt() != null &&
                !result.capturedAt().isBlank()) {
            appearance.setCapturedAt(
                    OffsetDateTime
                            .parse(result.capturedAt())
                            .toLocalDateTime()
            );
        }

        appearances.add(appearance);
    }

    faceAppearanceRepository.saveAll(appearances);

    job.setStatus("COMPLETED");
    job.setUpdatedAt(Instant.now());

    videoJobRepository.save(job);
}
private VideoJobResponse toResponse(VideoJob job) {
    return new VideoJobResponse(
            job.getId(),
            job.getOriginalFileName(),
            job.getBucket(),
            job.getObjectKey(),
            job.getCameraId(),
            job.getStatus(),
            job.getCreatedAt()
    );
}
private String detectContentType(String fileName) {
    String normalizedFileName =
            fileName.toLowerCase();

    if (normalizedFileName.endsWith(".mp4")) {
        return "video/mp4";
    }

    if (normalizedFileName.endsWith(".mov")) {
        return "video/quicktime";
    }

    if (normalizedFileName.endsWith(".avi")) {
        return "video/x-msvideo";
    }

    if (normalizedFileName.endsWith(".mkv")) {
        return "video/x-matroska";
    }

    return "application/octet-stream";
}
}