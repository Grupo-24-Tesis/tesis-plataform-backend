package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.domain.VideoJob;
import com.facecluster.facecluster_backend.domain.VideoJobStatus;
import com.facecluster.facecluster_backend.dto.VideoJobResponse;
import com.facecluster.facecluster_backend.dto.VideoResultRequest;
import com.facecluster.facecluster_backend.dto.VideoResultRequest.FaceAppearanceResult;
import com.facecluster.facecluster_backend.dto.VideoUploadedRequest;
import com.facecluster.facecluster_backend.messaging.InvalidWorkerEventException;
import com.facecluster.facecluster_backend.messaging.SqsVideoJobPublisher;
import com.facecluster.facecluster_backend.messaging.VideoProcessingEvent;
import com.facecluster.facecluster_backend.messaging.VideoProcessingMessage;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.VideoJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class VideoJobService {

    private final VideoJobRepository videoJobRepository;
    private final FaceAppearanceRepository faceAppearanceRepository;
    private final SqsVideoJobPublisher sqsVideoJobPublisher;
    private final long queuedTimeoutMinutes;
    private final long processingTimeoutMinutes;

    public VideoJobService(
            VideoJobRepository videoJobRepository,
            FaceAppearanceRepository faceAppearanceRepository,
            SqsVideoJobPublisher sqsVideoJobPublisher,
            @Value("${video-jobs.queued-timeout-minutes}") long queuedTimeoutMinutes,
            @Value("${video-jobs.processing-timeout-minutes}") long processingTimeoutMinutes
    ) {
        this.videoJobRepository = videoJobRepository;
        this.faceAppearanceRepository = faceAppearanceRepository;
        this.sqsVideoJobPublisher = sqsVideoJobPublisher;
        this.queuedTimeoutMinutes = queuedTimeoutMinutes;
        this.processingTimeoutMinutes = processingTimeoutMinutes;
    }

    public VideoJobResponse registerUploadedVideo(VideoUploadedRequest request) {
        validateRequest(request);

        Instant now = Instant.now();

        VideoJob job = new VideoJob();
        job.setOriginalFileName(request.originalFileName().trim());
        job.setBucket(request.bucket().trim());
        job.setObjectKey(request.objectKey().trim());
        job.setCameraId(request.cameraId().trim());
        job.setStatus(VideoJobStatus.UPLOADED);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);

        VideoJob savedJob = videoJobRepository.save(job);

        VideoProcessingMessage message = new VideoProcessingMessage(
                "VIDEO_UPLOAD_READY",
                "1",
                savedJob.getId(),
                savedJob.getBucket(),
                savedJob.getObjectKey(),
                savedJob.getCameraId(),
                savedJob.getOriginalFileName(),
                detectContentType(savedJob.getOriginalFileName()),
                savedJob.getCreatedAt()
        );

        try {
            sqsVideoJobPublisher.publish(message);

            savedJob.setStatus(VideoJobStatus.QUEUED);
            savedJob.setUpdatedAt(Instant.now());

            savedJob = videoJobRepository.save(savedJob);

        } catch (RuntimeException exception) {
            savedJob.setStatus(VideoJobStatus.QUEUE_ERROR);
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

    public VideoJobResponse getJobById(String jobId) {
        return toResponse(findJob(jobId));
    }

    public void processResults(String jobId, VideoResultRequest request) {
        VideoJob job = findJob(jobId);

        try {
            completeJob(job, request == null ? null : request.appearances());
        } catch (InvalidWorkerEventException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage(),
                    exception
            );
        }
    }

    public void applyWorkerEvent(VideoProcessingEvent event) {
        if (event == null || isBlank(event.jobId()) || isBlank(event.eventType())) {
            throw new InvalidWorkerEventException(
                    "El evento no tiene jobId o eventType"
            );
        }

        VideoJob job = videoJobRepository.findById(event.jobId())
                .orElseThrow(() ->
                        new InvalidWorkerEventException("Job no encontrado")
                );

        Instant sentAt = event.sentAt() != null ? event.sentAt() : Instant.now();

        switch (event.eventType()) {
            case VideoProcessingEvent.STARTED -> markProcessing(job, sentAt);
            case VideoProcessingEvent.FAILED -> markFailed(job, event.reason(), sentAt);
            case VideoProcessingEvent.COMPLETED -> completeJob(job, event.appearances());
            default -> throw new InvalidWorkerEventException(
                    "eventType desconocido: " + event.eventType()
            );
        }
    }

    public int failStaleJobs(Instant now) {
        int failed = 0;

        failed += failJobsWithoutResponse(
                VideoJobStatus.QUEUED,
                queuedTimeoutMinutes,
                "El pipeline no tomó el video en " + queuedTimeoutMinutes + " minutos",
                now
        );

        failed += failJobsWithoutResponse(
                VideoJobStatus.PROCESSING,
                processingTimeoutMinutes,
                "El pipeline dejó de reportar avance durante "
                        + processingTimeoutMinutes + " minutos",
                now
        );

        return failed;
    }

    private int failJobsWithoutResponse(
            String status,
            long timeoutMinutes,
            String reason,
            Instant now
    ) {
        List<VideoJob> staleJobs = videoJobRepository.findByStatusAndUpdatedAtBefore(
                status,
                now.minus(Duration.ofMinutes(timeoutMinutes))
        );

        for (VideoJob job : staleJobs) {
            job.setStatus(VideoJobStatus.FAILED);
            job.setFailureReason(reason);
            job.setUpdatedAt(now);

            videoJobRepository.save(job);
        }

        return staleJobs.size();
    }

    private void markProcessing(VideoJob job, Instant sentAt) {
        if (VideoJobStatus.COMPLETED.equals(job.getStatus()) || isOutdated(job, sentAt)) {
            return;
        }

        job.setStatus(VideoJobStatus.PROCESSING);
        job.setFailureReason(null);
        job.setLastWorkerEventAt(sentAt);
        job.setUpdatedAt(Instant.now());

        videoJobRepository.save(job);
    }

    private void markFailed(VideoJob job, String reason, Instant sentAt) {
        if (VideoJobStatus.COMPLETED.equals(job.getStatus())) {
            return;
        }

        job.setStatus(VideoJobStatus.FAILED);
        job.setFailureReason(
                isBlank(reason) ? "El pipeline no pudo procesar el video" : reason.trim()
        );
        job.setLastWorkerEventAt(sentAt);
        job.setUpdatedAt(Instant.now());

        videoJobRepository.save(job);
    }

    private void completeJob(VideoJob job, List<FaceAppearanceResult> results) {
        if (results == null) {
            throw new InvalidWorkerEventException(
                    "La lista de apariciones es obligatoria"
            );
        }

        if (VideoJobStatus.COMPLETED.equals(job.getStatus())) {
            return;
        }

        List<FaceAppearance> appearances = new ArrayList<>();

        for (FaceAppearanceResult result : results) {
            if (result == null || isBlank(result.faceId())) {
                throw new InvalidWorkerEventException("faceId es obligatorio");
            }

            FaceAppearance appearance = new FaceAppearance();
            appearance.setFaceId(result.faceId());
            appearance.setTimestamp(result.timestamp());
            appearance.setFrameUrl(result.frameUrl());
            appearance.setCameraId(job.getCameraId());

            if (!isBlank(result.capturedAt())) {
                try {
                    appearance.setCapturedAt(
                            OffsetDateTime.parse(result.capturedAt()).toLocalDateTime()
                    );
                } catch (DateTimeParseException exception) {
                    throw new InvalidWorkerEventException(
                            "capturedAt no es una fecha ISO-8601 con offset: "
                                    + result.capturedAt()
                    );
                }
            }

            appearances.add(appearance);
        }

        faceAppearanceRepository.saveAll(appearances);

        job.setStatus(VideoJobStatus.COMPLETED);
        job.setFailureReason(null);
        job.setUpdatedAt(Instant.now());

        videoJobRepository.save(job);
    }

    private boolean isOutdated(VideoJob job, Instant sentAt) {
        return job.getLastWorkerEventAt() != null
                && sentAt.isBefore(job.getLastWorkerEventAt());
    }

    private VideoJob findJob(String jobId) {
        return videoJobRepository.findById(jobId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Job no encontrado"
                        )
                );
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

    private VideoJobResponse toResponse(VideoJob job) {
        return new VideoJobResponse(
                job.getId(),
                job.getOriginalFileName(),
                job.getBucket(),
                job.getObjectKey(),
                job.getCameraId(),
                job.getStatus(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getFailureReason()
        );
    }

    private String detectContentType(String fileName) {
        String normalizedFileName = fileName.toLowerCase();

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
