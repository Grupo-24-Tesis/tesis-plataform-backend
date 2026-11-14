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
import com.facecluster.facecluster_backend.repository.CameraRepository;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.VideoJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class VideoJobService {

    private static final Logger log = LoggerFactory.getLogger(VideoJobService.class);

    private final VideoJobRepository videoJobRepository;
    private final FaceAppearanceRepository faceAppearanceRepository;
    private final CameraRepository cameraRepository;
    private final SqsVideoJobPublisher sqsVideoJobPublisher;
    private final String bucket;
    private final Clock clock;
    private final long uploadedTimeoutMinutes;
    private final long queuedTimeoutMinutes;
    private final long processingTimeoutMinutes;

    public VideoJobService(
            VideoJobRepository videoJobRepository,
            FaceAppearanceRepository faceAppearanceRepository,
            CameraRepository cameraRepository,
            SqsVideoJobPublisher sqsVideoJobPublisher,
            @Value("${aws.s3.bucket}") String bucket,
            Clock clock,
            @Value("${video-jobs.uploaded-timeout-minutes}") long uploadedTimeoutMinutes,
            @Value("${video-jobs.queued-timeout-minutes}") long queuedTimeoutMinutes,
            @Value("${video-jobs.processing-timeout-minutes}") long processingTimeoutMinutes
    ) {
        this.videoJobRepository = videoJobRepository;
        this.faceAppearanceRepository = faceAppearanceRepository;
        this.cameraRepository = cameraRepository;
        this.sqsVideoJobPublisher = sqsVideoJobPublisher;
        this.bucket = bucket;
        this.clock = clock;
        this.uploadedTimeoutMinutes = uploadedTimeoutMinutes;
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

        return toResponse(publishAndMarkQueued(videoJobRepository.save(job)));
    }

    public VideoJobResponse retryJob(String jobId) {
        VideoJob job = findJob(jobId);

        if (!VideoJobStatus.FAILED.equals(job.getStatus())
                && !VideoJobStatus.QUEUE_ERROR.equals(job.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solo se pueden reintentar videos fallidos"
            );
        }

        faceAppearanceRepository.deleteByJobId(job.getId());

        job.setFailureReason(null);
        job.setStartedAt(null);
        job.setCompletedAt(null);
        job.setFaceCount(null);
        job.setLastWorkerEventAt(null);

        return toResponse(publishAndMarkQueued(job));
    }

    private VideoJob publishAndMarkQueued(VideoJob job) {
        VideoProcessingMessage message = new VideoProcessingMessage(
                "VIDEO_UPLOAD_READY",
                "1",
                job.getId(),
                job.getBucket(),
                job.getObjectKey(),
                job.getCameraId(),
                job.getOriginalFileName(),
                detectContentType(job.getOriginalFileName()),
                job.getCreatedAt()
        );

        try {
            sqsVideoJobPublisher.publish(message);
        } catch (RuntimeException exception) {
            job.setStatus(VideoJobStatus.QUEUE_ERROR);
            job.setUpdatedAt(Instant.now());

            videoJobRepository.save(job);

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "El video se guardó, pero no pudo enviarse a la cola SQS",
                    exception
            );
        }

        try {
            job.setStatus(VideoJobStatus.QUEUED);
            job.setUpdatedAt(Instant.now());

            return videoJobRepository.save(job);
        } catch (RuntimeException exception) {
            log.error(
                    "El mensaje del job {} se envió a la cola, pero no se pudo guardar el estado QUEUED",
                    job.getId(),
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "El video se envió a la cola, pero no se pudo actualizar su estado",
                    exception
            );
        }
    }

    public VideoJobResponse getJobById(String jobId) {
        return toResponse(findJob(jobId));
    }

    public List<VideoJobResponse> listJobs(String cameraId) {
        List<VideoJob> jobs = isBlank(cameraId)
                ? videoJobRepository.findAllByOrderByCreatedAtDesc()
                : videoJobRepository.findByCameraIdOrderByCreatedAtDesc(cameraId.trim());

        return jobs.stream().map(this::toResponse).toList();
    }

    public void processResults(String jobId, VideoResultRequest request) {
        VideoJob job = findJob(jobId);

        try {
            completeJob(job, request == null ? null : request.appearances(), null);
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
                        new InvalidWorkerEventException("Trabajo no encontrado")
                );

        Instant sentAt = event.sentAt() != null ? event.sentAt() : Instant.now();

        switch (event.eventType()) {
            case VideoProcessingEvent.STARTED -> markProcessing(job, sentAt);
            case VideoProcessingEvent.FAILED -> markFailed(job, event.reason(), sentAt);
            case VideoProcessingEvent.COMPLETED ->
                    completeJob(job, event.appearances(), event.sentAt());
            default -> throw new InvalidWorkerEventException(
                    "eventType desconocido: " + event.eventType()
            );
        }
    }

    public int failStaleJobs(Instant now) {
        int failed = 0;

        failed += failJobsWithoutResponse(
                VideoJobStatus.UPLOADED,
                uploadedTimeoutMinutes,
                "El video no se pudo enviar a la cola en " + uploadedTimeoutMinutes + " minutos",
                now
        );

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

        if (job.getStartedAt() == null) {
            job.setStartedAt(sentAt);
        }

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

    private void completeJob(
            VideoJob job,
            List<FaceAppearanceResult> results,
            Instant sentAt
    ) {
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

            if (!result.faceId().startsWith(job.getId() + "_")) {
                throw new InvalidWorkerEventException(
                        "El faceId " + result.faceId() + " no pertenece al trabajo " + job.getId()
                );
            }

            FaceAppearance appearance = new FaceAppearance();
            appearance.setId(result.faceId());
            appearance.setJobId(job.getId());
            appearance.setFaceId(result.faceId());
            appearance.setTimestamp(result.timestamp());
            appearance.setFrameUrl(result.frameUrl());
            appearance.setCameraId(job.getCameraId());

            if (!isBlank(result.capturedAt())) {
                try {
                    appearance.setCapturedAt(
                            OffsetDateTime.parse(result.capturedAt())
                                    .atZoneSameInstant(clock.getZone())
                                    .toLocalDateTime()
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
        job.setCompletedAt(sentAt != null ? sentAt : Instant.now(clock));
        job.setFaceCount(appearances.size());
        job.setUpdatedAt(Instant.now());

        videoJobRepository.save(job);
    }

    private boolean isOutdated(VideoJob job, Instant sentAt) {
        return job.getLastWorkerEventAt() != null
                && !sentAt.isAfter(job.getLastWorkerEventAt());
    }

    private VideoJob findJob(String jobId) {
        return videoJobRepository.findById(jobId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Trabajo no encontrado"
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

        if (!bucket.equals(request.bucket().trim())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El bucket no corresponde al configurado"
            );
        }

        String objectKey = request.objectKey().trim();
        if (!objectKey.startsWith("videos/") || objectKey.contains("..")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "objectKey no es válido"
            );
        }

        if (!cameraRepository.existsById(request.cameraId().trim())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La cámara no existe"
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
                job.getFailureReason(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getFaceCount(),
                job.queueWaitSeconds(),
                job.processingSeconds()
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
