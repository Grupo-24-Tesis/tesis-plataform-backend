package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.domain.VideoJob;
import com.facecluster.facecluster_backend.domain.VideoJobStatus;
import com.facecluster.facecluster_backend.dto.VideoJobResponse;
import com.facecluster.facecluster_backend.dto.VideoResultRequest;
import com.facecluster.facecluster_backend.dto.VideoUploadedRequest;
import com.facecluster.facecluster_backend.dto.VideoResultRequest.FaceAppearanceResult;
import com.facecluster.facecluster_backend.messaging.InvalidWorkerEventException;
import com.facecluster.facecluster_backend.messaging.SqsVideoJobPublisher;
import com.facecluster.facecluster_backend.messaging.VideoProcessingEvent;
import com.facecluster.facecluster_backend.messaging.VideoProcessingMessage;
import com.facecluster.facecluster_backend.repository.CameraRepository;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.FaceEmbeddingRepository;
import com.facecluster.facecluster_backend.repository.JobResultPartRepository;
import com.facecluster.facecluster_backend.domain.FaceEmbedding;
import com.facecluster.facecluster_backend.domain.JobResultPart;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import com.facecluster.facecluster_backend.repository.VideoJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoJobServiceTest {

    private static final String BUCKET = "bucket";
    private static final String MODEL = "GhostFaceNet";
    private static final long UPLOADED_TIMEOUT_MINUTES = 5;
    private static final long QUEUED_TIMEOUT_MINUTES = 720;
    private static final long PROCESSING_TIMEOUT_MINUTES = 15;
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @Mock
    private VideoJobRepository videoJobRepository;

    @Mock
    private FaceAppearanceRepository faceAppearanceRepository;

    @Mock
    private FaceEmbeddingRepository faceEmbeddingRepository;

    @Mock
    private JobResultPartRepository jobResultPartRepository;

    @Mock
    private CameraRepository cameraRepository;

    @Mock
    private SqsVideoJobPublisher sqsVideoJobPublisher;

    @Mock
    private FaceMatchService faceMatchService;

    private VideoJobService service;

    @BeforeEach
    void setUp() {
        service = new VideoJobService(
                videoJobRepository,
                faceAppearanceRepository,
                faceEmbeddingRepository,
                jobResultPartRepository,
                cameraRepository,
                sqsVideoJobPublisher,
                BUCKET,
                Clock.system(ZoneId.of("America/Bogota")),
                UPLOADED_TIMEOUT_MINUTES,
                QUEUED_TIMEOUT_MINUTES,
                PROCESSING_TIMEOUT_MINUTES,
                MODEL,
                faceMatchService
        );
    }

    @Test
    void listsAllJobsNewestFirstWhenThereIsNoCameraId() {
        VideoJob newer = new VideoJob();
        newer.setId("job-2");
        VideoJob older = new VideoJob();
        older.setId("job-1");
        when(videoJobRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(newer, older));

        List<VideoJobResponse> result = service.listJobs(null);

        assertThat(result).extracting(VideoJobResponse::jobId)
                .containsExactly("job-2", "job-1");
        verify(videoJobRepository, never()).findByCameraIdOrderByCreatedAtDesc(any());
    }

    @Test
    void listsJobsOfACameraNewestFirst() {
        VideoJob job = new VideoJob();
        job.setId("job-3");
        job.setCameraId("cam-1");
        when(videoJobRepository.findByCameraIdOrderByCreatedAtDesc("cam-1"))
                .thenReturn(List.of(job));

        List<VideoJobResponse> result = service.listJobs("cam-1");

        assertThat(result).extracting(VideoJobResponse::cameraId).containsExactly("cam-1");
        verify(videoJobRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void startedEventMovesJobToProcessing() {
        VideoJob job = storedJob(VideoJobStatus.QUEUED);

        service.applyWorkerEvent(started(NOW));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.PROCESSING);
        assertThat(job.getLastWorkerEventAt()).isEqualTo(NOW);
        verify(videoJobRepository).save(job);
    }

    @Test
    void startedEventRevivesJobFailedByTimeout() {
        VideoJob job = storedJob(VideoJobStatus.FAILED);
        job.setFailureReason("sin respuesta");

        service.applyWorkerEvent(started(NOW));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.PROCESSING);
        assertThat(job.getFailureReason()).isNull();
    }

    @Test
    void startedEventIsIgnoredWhenJobIsCompleted() {
        VideoJob job = storedJob(VideoJobStatus.COMPLETED);

        service.applyWorkerEvent(started(NOW));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.COMPLETED);
        verify(videoJobRepository, never()).save(any());
    }

    @Test
    void startedEventOlderThanLastWorkerEventIsIgnored() {
        VideoJob job = storedJob(VideoJobStatus.FAILED);
        job.setLastWorkerEventAt(NOW);

        service.applyWorkerEvent(started(NOW.minusSeconds(5)));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.FAILED);
        verify(videoJobRepository, never()).save(any());
    }

    @Test
    void startedEventWithSameSentAtAsLastWorkerEventIsIgnored() {
        VideoJob job = storedJob(VideoJobStatus.FAILED);
        job.setLastWorkerEventAt(NOW);

        service.applyWorkerEvent(started(NOW));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.FAILED);
        verify(videoJobRepository, never()).save(any());
    }

    @Test
    void failedEventStoresReason() {
        VideoJob job = storedJob(VideoJobStatus.PROCESSING);

        service.applyWorkerEvent(failed(NOW, "video no encontrado"));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.FAILED);
        assertThat(job.getFailureReason()).isEqualTo("video no encontrado");
        assertThat(job.getLastWorkerEventAt()).isEqualTo(NOW);
        verify(videoJobRepository).save(job);
    }

    @Test
    void failedEventIsIgnoredWhenJobIsCompleted() {
        VideoJob job = storedJob(VideoJobStatus.COMPLETED);

        service.applyWorkerEvent(failed(NOW, "tarde"));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.COMPLETED);
        verify(videoJobRepository, never()).save(any());
    }

    @Test
    void failedEventOlderThanLastWorkerEventIsIgnored() {
        VideoJob job = storedJob(VideoJobStatus.PROCESSING);
        job.setLastWorkerEventAt(NOW);

        service.applyWorkerEvent(failed(NOW.minusSeconds(5), "tarde"));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.PROCESSING);
        assertThat(job.getFailureReason()).isNull();
        verify(videoJobRepository, never()).save(any());
    }

    @Test
    void failedEventWithSameSentAtAsLastWorkerEventIsIgnored() {
        VideoJob job = storedJob(VideoJobStatus.PROCESSING);
        job.setLastWorkerEventAt(NOW);

        service.applyWorkerEvent(failed(NOW, "repetido"));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.PROCESSING);
        verify(videoJobRepository, never()).save(any());
    }

    @Test
    void failedEventNewerThanLastWorkerEventIsApplied() {
        VideoJob job = storedJob(VideoJobStatus.PROCESSING);
        job.setLastWorkerEventAt(NOW);

        service.applyWorkerEvent(failed(NOW.plusSeconds(5), "error real"));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.FAILED);
        assertThat(job.getFailureReason()).isEqualTo("error real");
        assertThat(job.getLastWorkerEventAt()).isEqualTo(NOW.plusSeconds(5));
    }

    @Test
    void completedEventStoresAppearancesWithTheJobCamera() {
        VideoJob job = storedJob(VideoJobStatus.PROCESSING);

        service.applyWorkerEvent(completed(NOW, List.of(
                new FaceAppearanceResult(
                        "job-1_4",
                        "00:01:23",
                        "https://bucket.s3.us-east-2.amazonaws.com/faces/job-1/4.jpg",
                        "2026-10-16T15:05:21Z"
                )
        )));

        ArgumentCaptor<List<FaceAppearance>> saved = ArgumentCaptor.forClass(List.class);
        verify(faceAppearanceRepository).saveAll(saved.capture());
        FaceAppearance appearance = saved.getValue().get(0);
        assertThat(appearance.getId()).isEqualTo("job-1_4");
        assertThat(appearance.getJobId()).isEqualTo("job-1");
        assertThat(appearance.getFaceId()).isEqualTo("job-1_4");
        assertThat(appearance.getCameraId()).isEqualTo("CAM-01");
        assertThat(appearance.getTimestamp()).isEqualTo("00:01:23");
        assertThat(appearance.getCapturedAt()).hasToString("2026-10-16T10:05:21");
        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.COMPLETED);
    }

    @Test
    void redeliveredCompletedEventAfterJobSaveFailureStoresAppearancesWithTheSameIds() {
        VideoJob firstDelivery = job("job-1", VideoJobStatus.PROCESSING);
        VideoJob secondDelivery = job("job-1", VideoJobStatus.PROCESSING);
        when(videoJobRepository.findById("job-1"))
                .thenReturn(Optional.of(firstDelivery), Optional.of(secondDelivery));
        when(videoJobRepository.save(firstDelivery))
                .thenThrow(new IllegalStateException("fallo al guardar el job"));
        VideoProcessingEvent event = completed(NOW, List.of(
                new FaceAppearanceResult("job-1_4", "00:01:23", "url-4", null),
                new FaceAppearanceResult("job-1_7", "00:02:10", "url-7", null)
        ));

        assertThatThrownBy(() -> service.applyWorkerEvent(event))
                .isInstanceOf(IllegalStateException.class);
        service.applyWorkerEvent(event);

        ArgumentCaptor<List<FaceAppearance>> saved = ArgumentCaptor.forClass(List.class);
        verify(faceAppearanceRepository, times(2)).saveAll(saved.capture());
        List<List<String>> savedIds = saved.getAllValues().stream()
                .map(list -> list.stream().map(FaceAppearance::getId).toList())
                .toList();
        assertThat(savedIds.get(0)).containsExactly("job-1_4", "job-1_7");
        assertThat(savedIds.get(1)).isEqualTo(savedIds.get(0));
        assertThat(secondDelivery.getStatus()).isEqualTo(VideoJobStatus.COMPLETED);
    }

    @Test
    void completedEventAfterFailureCompletesJobAndClearsReason() {
        VideoJob job = storedJob(VideoJobStatus.FAILED);
        job.setFailureReason("sin respuesta");

        service.applyWorkerEvent(completed(NOW, List.of()));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.COMPLETED);
        assertThat(job.getFailureReason()).isNull();
    }

    @Test
    void duplicatedCompletedEventDoesNotStoreAppearancesAgain() {
        storedJob(VideoJobStatus.COMPLETED);

        service.applyWorkerEvent(completed(NOW, List.of(
                new FaceAppearanceResult("job-1_4", "00:00:01", "url", null)
        )));

        verify(faceAppearanceRepository, never()).saveAll(anyList());
        verify(videoJobRepository, never()).save(any());
    }

    @Test
    void completedEventWithoutAppearancesListIsInvalid() {
        storedJob(VideoJobStatus.PROCESSING);

        assertThatThrownBy(() -> service.applyWorkerEvent(completed(NOW, null)))
                .isInstanceOf(InvalidWorkerEventException.class);
    }

    @Test
    void eventForUnknownJobIsInvalid() {
        when(videoJobRepository.findById("job-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyWorkerEvent(started(NOW)))
                .isInstanceOf(InvalidWorkerEventException.class);
    }

    @Test
    void eventWithUnknownTypeIsInvalid() {
        storedJob(VideoJobStatus.QUEUED);
        VideoProcessingEvent event = new VideoProcessingEvent(
                "OTRO", "1", "job-1", NOW, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> service.applyWorkerEvent(event))
                .isInstanceOf(InvalidWorkerEventException.class);
    }

    @Test
    void completedEventWithFaceIdOfAnotherJobIsInvalid() {
        VideoJob job = storedJob(VideoJobStatus.PROCESSING);

        assertThatThrownBy(() -> service.applyWorkerEvent(completed(NOW, List.of(
                new FaceAppearanceResult("job-1_4", "00:00:01", "url-4", null),
                new FaceAppearanceResult("job-2_7", "00:00:02", "url-7", null)
        )))).isInstanceOf(InvalidWorkerEventException.class)
                .hasMessage("El faceId job-2_7 no pertenece al trabajo job-1");

        verify(faceAppearanceRepository, never()).saveAll(anyList());
        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.PROCESSING);
    }

    @Test
    void completedEventWithFaceIdThatOnlySharesThePrefixIsInvalid() {
        storedJob(VideoJobStatus.PROCESSING);

        assertThatThrownBy(() -> service.applyWorkerEvent(completed(NOW, List.of(
                new FaceAppearanceResult("job-10_4", "00:00:01", "url-4", null)
        )))).isInstanceOf(InvalidWorkerEventException.class);

        verify(faceAppearanceRepository, never()).saveAll(anyList());
    }

    @Test
    void restResultsWithFaceIdOfAnotherJobAnswerBadRequest() {
        storedJob(VideoJobStatus.PROCESSING);

        assertThatThrownBy(() -> service.processResults(
                "job-1",
                new VideoResultRequest(List.of(
                        new FaceAppearanceResult("job-2_4", "00:00:01", "url-4", null)
                ))
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(exception.getReason())
                    .isEqualTo("El faceId job-2_4 no pertenece al trabajo job-1");
        });

        verify(faceAppearanceRepository, never()).saveAll(anyList());
    }

    @Test
    void restResultsForUnknownJobAnswerNotFound() {
        when(videoJobRepository.findById("job-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processResults(
                "job-1", new VideoResultRequest(List.of())
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void staleUploadedJobBecomesFailed() {
        VideoJob uploaded = job("job-u", VideoJobStatus.UPLOADED);
        when(videoJobRepository.findByStatusAndUpdatedAtBefore(
                VideoJobStatus.UPLOADED,
                NOW.minus(UPLOADED_TIMEOUT_MINUTES, ChronoUnit.MINUTES)
        )).thenReturn(List.of(uploaded));

        int failed = service.failStaleJobs(NOW);

        assertThat(failed).isEqualTo(1);
        assertThat(uploaded.getStatus()).isEqualTo(VideoJobStatus.FAILED);
        assertThat(uploaded.getFailureReason())
                .isEqualTo("El video no se pudo enviar a la cola en 5 minutos");
        verify(videoJobRepository).save(uploaded);
    }

    @Test
    void publishFailureMarksQueueError() {
        when(cameraRepository.existsById("CAM-01")).thenReturn(true);
        when(videoJobRepository.save(any(VideoJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("sqs caido"))
                .when(sqsVideoJobPublisher).publish(any());

        assertThatThrownBy(() -> service.registerUploadedVideo(uploadRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        ArgumentCaptor<VideoJob> saved = ArgumentCaptor.forClass(VideoJob.class);
        verify(videoJobRepository, times(2)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(VideoJobStatus.QUEUE_ERROR);
    }

    @Test
    void saveFailureAfterPublishDoesNotMarkQueueError() {
        when(cameraRepository.existsById("CAM-01")).thenReturn(true);
        List<String> savedStatuses = new java.util.ArrayList<>();
        when(videoJobRepository.save(any(VideoJob.class)))
                .thenAnswer(invocation -> {
                    VideoJob job = invocation.getArgument(0);
                    savedStatuses.add(job.getStatus());
                    if (VideoJobStatus.QUEUED.equals(job.getStatus())) {
                        throw new IllegalStateException("mongo caido");
                    }
                    return job;
                });

        assertThatThrownBy(() -> service.registerUploadedVideo(uploadRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));

        verify(sqsVideoJobPublisher).publish(any());
        assertThat(savedStatuses)
                .containsExactly(VideoJobStatus.UPLOADED, VideoJobStatus.QUEUED);
    }

    @Test
    void staleQueuedAndProcessingJobsBecomeFailed() {
        VideoJob queued = job("job-q", VideoJobStatus.QUEUED);
        VideoJob processing = job("job-p", VideoJobStatus.PROCESSING);
        when(videoJobRepository.findByStatusAndUpdatedAtBefore(
                VideoJobStatus.UPLOADED,
                NOW.minus(UPLOADED_TIMEOUT_MINUTES, ChronoUnit.MINUTES)
        )).thenReturn(List.of());
        when(videoJobRepository.findByStatusAndUpdatedAtBefore(
                VideoJobStatus.QUEUED,
                NOW.minus(QUEUED_TIMEOUT_MINUTES, ChronoUnit.MINUTES)
        )).thenReturn(List.of(queued));
        when(videoJobRepository.findByStatusAndUpdatedAtBefore(
                VideoJobStatus.PROCESSING,
                NOW.minus(PROCESSING_TIMEOUT_MINUTES, ChronoUnit.MINUTES)
        )).thenReturn(List.of(processing));

        int failed = service.failStaleJobs(NOW);

        assertThat(failed).isEqualTo(2);
        assertThat(queued.getStatus()).isEqualTo(VideoJobStatus.FAILED);
        assertThat(queued.getFailureReason()).contains("720");
        assertThat(queued.getUpdatedAt()).isEqualTo(NOW);
        assertThat(processing.getStatus()).isEqualTo(VideoJobStatus.FAILED);
        assertThat(processing.getFailureReason()).contains("15");
        verify(videoJobRepository).save(queued);
        verify(videoJobRepository).save(processing);
    }

    @Test
    void jobResponseIncludesFailureReason() {
        VideoJob job = storedJob(VideoJobStatus.FAILED);
        job.setFailureReason("video no encontrado");

        VideoJobResponse response = service.getJobById("job-1");

        assertThat(response.status()).isEqualTo(VideoJobStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("video no encontrado");
    }

    @Test
    void startedEventSetsStartedAtOnlyTheFirstTime() {
        VideoJob job = storedJob(VideoJobStatus.QUEUED);

        service.applyWorkerEvent(started(NOW));
        service.applyWorkerEvent(started(NOW.plusSeconds(30)));

        assertThat(job.getStartedAt()).isEqualTo(NOW);
        assertThat(job.getLastWorkerEventAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void failedEventKeepsStartedAt() {
        VideoJob job = storedJob(VideoJobStatus.PROCESSING);
        job.setStartedAt(NOW);

        service.applyWorkerEvent(failed(NOW.plusSeconds(10), "error"));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.FAILED);
        assertThat(job.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    void completedEventStoresCompletedAtAndFaceCount() {
        VideoJob job = storedJob(VideoJobStatus.PROCESSING);

        service.applyWorkerEvent(completed(NOW, List.of(
                new FaceAppearanceResult("job-1_1", "00:00:01", "url", null),
                new FaceAppearanceResult("job-1_2", "00:00:02", "url", null)
        )));

        assertThat(job.getCompletedAt()).isEqualTo(NOW);
        assertThat(job.getFaceCount()).isEqualTo(2);
    }

    @Test
    void completedEventWithoutSentAtUsesTheClock() {
        Instant clockInstant = Instant.parse("2026-11-14T15:30:00Z");
        useFixedClock(clockInstant);
        VideoJob job = storedJob(VideoJobStatus.PROCESSING);

        service.applyWorkerEvent(completed(null, List.of()));

        assertThat(job.getCompletedAt()).isEqualTo(clockInstant);
        assertThat(job.getFaceCount()).isZero();
    }

    @Test
    void jobResponseIncludesDerivedSeconds() {
        VideoJob job = storedJob(VideoJobStatus.COMPLETED);
        job.setCreatedAt(NOW);
        job.setStartedAt(NOW.plusSeconds(12));
        job.setCompletedAt(NOW.plusSeconds(92));
        job.setFaceCount(3);

        VideoJobResponse response = service.getJobById("job-1");

        assertThat(response.startedAt()).isEqualTo(NOW.plusSeconds(12));
        assertThat(response.completedAt()).isEqualTo(NOW.plusSeconds(92));
        assertThat(response.faceCount()).isEqualTo(3);
        assertThat(response.queueWaitSeconds()).isEqualTo(12L);
        assertThat(response.processingSeconds()).isEqualTo(80L);
    }

    @Test
    void jobResponseClampsNegativeSecondsToZero() {
        VideoJob job = storedJob(VideoJobStatus.COMPLETED);
        job.setCreatedAt(NOW);
        job.setStartedAt(NOW.minusSeconds(5));
        job.setCompletedAt(NOW.minusSeconds(20));

        VideoJobResponse response = service.getJobById("job-1");

        assertThat(response.queueWaitSeconds()).isZero();
        assertThat(response.processingSeconds()).isZero();
    }

    @Test
    void jobResponseLeavesSecondsEmptyForOldJobs() {
        storedJob(VideoJobStatus.COMPLETED);

        VideoJobResponse response = service.getJobById("job-1");

        assertThat(response.queueWaitSeconds()).isNull();
        assertThat(response.processingSeconds()).isNull();
        assertThat(response.faceCount()).isNull();
    }

    @Test
    void registerRejectsAnotherBucket() {
        VideoUploadedRequest request =
                new VideoUploadedRequest("video.mp4", "otro", "videos/video.mp4", "CAM-01");

        assertBadRequest(request, "El bucket no corresponde al configurado");
    }

    @Test
    void registerRejectsKeysOutsideVideosPrefix() {
        assertBadRequest(
                new VideoUploadedRequest("x.mp4", BUCKET, "otros/x.mp4", "CAM-01"),
                "objectKey no es válido"
        );
        assertBadRequest(
                new VideoUploadedRequest("x.mp4", BUCKET, "videos/../x", "CAM-01"),
                "objectKey no es válido"
        );
    }

    @Test
    void registerRejectsUnknownCamera() {
        when(cameraRepository.existsById("CAM-99")).thenReturn(false);

        assertBadRequest(
                new VideoUploadedRequest("x.mp4", BUCKET, "videos/x.mp4", "CAM-99"),
                "La cámara no existe"
        );
    }

    @Test
    void registerWithValidRequestPublishes() {
        when(cameraRepository.existsById("CAM-01")).thenReturn(true);
        when(videoJobRepository.save(any(VideoJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registerUploadedVideo(uploadRequest());

        verify(sqsVideoJobPublisher).publish(any());
    }

    @Test
    void retryOfMissingJobIsNotFound() {
        when(videoJobRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retryJob("missing"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("Trabajo no encontrado");
                });
    }

    @Test
    void retryOfNonRetryableStatusesIsConflict() {
        for (String status : List.of(
                VideoJobStatus.UPLOADED,
                VideoJobStatus.QUEUED,
                VideoJobStatus.PROCESSING,
                VideoJobStatus.COMPLETED
        )) {
            storedJob(status);

            assertThatThrownBy(() -> service.retryJob("job-1"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(exception.getReason())
                                .isEqualTo("Solo se pueden reintentar videos fallidos");
                    });
        }

        verify(faceAppearanceRepository, never()).deleteByJobId(any());
        verify(sqsVideoJobPublisher, never()).publish(any());
        verify(videoJobRepository, never()).save(any());
    }

    @Test
    void retryRefusedWhenDetectionHasConfirmedMatch() {
        failedJob(VideoJobStatus.FAILED);
        FaceAppearance appearance = new FaceAppearance();
        appearance.setFaceId("job-1_1");
        when(faceAppearanceRepository.findByJobIdOrderByTimestampAsc("job-1"))
                .thenReturn(List.of(appearance));
        when(faceMatchService.hasConfirmedMatch(List.of("job-1_1"))).thenReturn(true);

        assertThatThrownBy(() -> service.retryJob("job-1"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).isEqualTo(
                            "La detección tiene coincidencias confirmadas; quítelas antes de reintentar");
                });

        verify(faceAppearanceRepository, never()).deleteByJobId(any());
        verify(sqsVideoJobPublisher, never()).publish(any());
    }

    @Test
    void retryOfFailedOrQueueErrorJobRequeuesIt() {
        for (String status : List.of(VideoJobStatus.FAILED, VideoJobStatus.QUEUE_ERROR)) {
            VideoJob job = failedJob(status);
            when(videoJobRepository.save(any(VideoJob.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            VideoJobResponse response = service.retryJob("job-1");

            assertThat(response.status()).isEqualTo(VideoJobStatus.QUEUED);
            assertThat(job.getStatus()).isEqualTo(VideoJobStatus.QUEUED);
            assertThat(job.getFailureReason()).isNull();
            assertThat(job.getStartedAt()).isNull();
            assertThat(job.getCompletedAt()).isNull();
            assertThat(job.getFaceCount()).isNull();
            assertThat(job.getLastWorkerEventAt()).isAfter(NOW.minus(1, ChronoUnit.HOURS));
            assertThat(job.getQueuedAt()).isNotNull();
            assertThat(job.getUpdatedAt()).isAfter(NOW.minus(1, ChronoUnit.HOURS));
        }

        InOrder order = inOrder(faceAppearanceRepository, sqsVideoJobPublisher);
        order.verify(faceAppearanceRepository).deleteByJobId("job-1");
        order.verify(sqsVideoJobPublisher).publish(any());
        order.verify(faceAppearanceRepository).deleteByJobId("job-1");
        order.verify(sqsVideoJobPublisher).publish(any());

        ArgumentCaptor<VideoProcessingMessage> message =
                ArgumentCaptor.forClass(VideoProcessingMessage.class);
        verify(sqsVideoJobPublisher, times(2)).publish(message.capture());
        VideoProcessingMessage published = message.getValue();
        assertThat(published.eventType()).isEqualTo("VIDEO_UPLOAD_READY");
        assertThat(published.eventVersion()).isEqualTo("1");
        assertThat(published.jobId()).isEqualTo("job-1");
        assertThat(published.bucket()).isEqualTo(BUCKET);
        assertThat(published.objectKey()).isEqualTo("videos/video.mp4");
        assertThat(published.cameraId()).isEqualTo("CAM-01");
        assertThat(published.originalFileName()).isEqualTo("video.mp4");
        assertThat(published.contentType()).isEqualTo("video/mp4");
        assertThat(published.createdAt()).isEqualTo(NOW.minus(1, ChronoUnit.DAYS));
    }

    @Test
    void uploadSetsQueuedAtFromTheClock() {
        useFixedClock(NOW);
        when(cameraRepository.existsById("CAM-01")).thenReturn(true);
        when(videoJobRepository.save(any(VideoJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registerUploadedVideo(uploadRequest());

        ArgumentCaptor<VideoJob> saved = ArgumentCaptor.forClass(VideoJob.class);
        verify(videoJobRepository, times(2)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(VideoJobStatus.QUEUED);
        assertThat(saved.getValue().getQueuedAt()).isEqualTo(NOW);
    }

    @Test
    void retryMarksTheRetryInstantAsLastWorkerEventAndQueuedAt() {
        useFixedClock(NOW);
        VideoJob job = failedJob(VideoJobStatus.FAILED);
        when(videoJobRepository.save(any(VideoJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.retryJob("job-1");

        assertThat(job.getLastWorkerEventAt()).isEqualTo(NOW);
        assertThat(job.getQueuedAt()).isEqualTo(NOW);
    }

    @Test
    void startedEventAfterRetryMeasuresQueueWaitFromTheRetry() {
        useFixedClock(NOW);
        VideoJob job = failedJob(VideoJobStatus.FAILED);
        when(videoJobRepository.save(any(VideoJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.retryJob("job-1");
        service.applyWorkerEvent(started(NOW.plusSeconds(30)));

        VideoJobResponse response = service.getJobById("job-1");
        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.PROCESSING);
        assertThat(response.queueWaitSeconds()).isEqualTo(30L);
    }

    @Test
    void redeliveredEventsOfThePreviousAttemptAreIgnoredAfterRetry() {
        useFixedClock(NOW);
        VideoJob job = failedJob(VideoJobStatus.FAILED);
        when(videoJobRepository.save(any(VideoJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.retryJob("job-1");
        service.applyWorkerEvent(started(NOW.minus(2, ChronoUnit.HOURS)));
        service.applyWorkerEvent(failed(NOW.minus(1, ChronoUnit.HOURS), "fallo anterior"));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.QUEUED);
        assertThat(job.getStartedAt()).isNull();
        assertThat(job.getFailureReason()).isNull();
        verify(videoJobRepository, times(1)).save(job);
    }

    @Test
    void jobResponseMeasuresQueueWaitFromQueuedAtWhenPresent() {
        VideoJob job = storedJob(VideoJobStatus.PROCESSING);
        job.setCreatedAt(NOW.minus(1, ChronoUnit.DAYS));
        job.setQueuedAt(NOW);
        job.setStartedAt(NOW.plusSeconds(7));

        VideoJobResponse response = service.getJobById("job-1");

        assertThat(response.queueWaitSeconds()).isEqualTo(7L);
    }

    @Test
    void retryPublishFailureMarksQueueError() {
        VideoJob job = failedJob(VideoJobStatus.FAILED);
        when(videoJobRepository.save(any(VideoJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("sqs caido"))
                .when(sqsVideoJobPublisher).publish(any());

        assertThatThrownBy(() -> service.retryJob("job-1"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.QUEUE_ERROR);
        verify(faceAppearanceRepository).deleteByJobId("job-1");
        verify(videoJobRepository).save(job);
    }

    @Test
    void singleCompletedEventStoresEmbeddings() {
        VideoJob job = storedJob(VideoJobStatus.PROCESSING);

        service.applyWorkerEvent(embeddingEvent(
                null, null, null,
                List.of(face("job-1_1", 2, 0, 0), face("job-1_2", 0, 3, 0))
        ));

        ArgumentCaptor<List<FaceEmbedding>> captor = ArgumentCaptor.forClass(List.class);
        verify(faceEmbeddingRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        FaceEmbedding first = captor.getValue().get(0);
        assertThat(first.getId()).isEqualTo("job-1_1:GhostFaceNet");
        assertThat(first.getFaceId()).isEqualTo("job-1_1");
        assertThat(first.getJobId()).isEqualTo("job-1");
        assertThat(first.getCameraId()).isEqualTo("CAM-01");
        assertThat(first.getModel()).isEqualTo("GhostFaceNet");
        assertThat(first.getDimensions()).isEqualTo(3);
        assertThat(first.getVector())
                .isEqualTo(EmbeddingCodec.toBytes(new float[] {1, 0, 0}));
        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.COMPLETED);
    }

    @Test
    void appearanceWithoutEmbeddingIsStored() {
        VideoJob job = storedJob(VideoJobStatus.PROCESSING);

        service.applyWorkerEvent(embeddingEvent(
                null, null, null,
                List.of(
                        face("job-1_1", 1, 0, 0),
                        new FaceAppearanceResult("job-1_2", "00:00:02", "url", null)
                )
        ));

        ArgumentCaptor<List<FaceAppearance>> appearances = ArgumentCaptor.forClass(List.class);
        verify(faceAppearanceRepository).saveAll(appearances.capture());
        assertThat(appearances.getValue()).hasSize(2);
        ArgumentCaptor<List<FaceEmbedding>> embeddings = ArgumentCaptor.forClass(List.class);
        verify(faceEmbeddingRepository).saveAll(embeddings.capture());
        assertThat(embeddings.getValue()).extracting(FaceEmbedding::getFaceId)
                .containsExactly("job-1_1");
        assertThat(job.getFaceCount()).isEqualTo(2);
    }

    @Test
    void partsAreStoredUntilLastArrives() {
        VideoJob job = storedJob(VideoJobStatus.PROCESSING);
        Map<String, JobResultPart> store = partStore();

        service.applyWorkerEvent(embeddingEvent(
                "attempt-x", 1, 2, List.of(face("job-1_1", 1, 0, 0))
        ));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.PROCESSING);
        assertThat(store).containsOnlyKeys("job-1:attempt-x:1");
        assertThat(job.getLastWorkerEventAt()).isEqualTo(NOW);
        verify(faceAppearanceRepository, never()).saveAll(anyList());
        verify(videoJobRepository).save(job);
    }

    @Test
    void completesFromOneFullAttemptWithPartsOutOfOrderAndRepeated() {
        VideoJob job = storedJob(VideoJobStatus.PROCESSING);
        Map<String, JobResultPart> store = partStore();

        service.applyWorkerEvent(embeddingEvent(
                "attempt-x", 2, 2, List.of(face("job-1_3", 0, 0, 1))
        ));
        service.applyWorkerEvent(embeddingEvent(
                "attempt-y", 1, 3, List.of(face("job-1_9", 0, 1, 0))
        ));
        service.applyWorkerEvent(embeddingEvent(
                "attempt-x", 2, 2, List.of(face("job-1_3", 0, 0, 1))
        ));
        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.PROCESSING);
        service.applyWorkerEvent(embeddingEvent(
                "attempt-x", 1, 2,
                List.of(face("job-1_1", 1, 0, 0), face("job-1_2", 1, 1, 0))
        ));

        ArgumentCaptor<List<FaceAppearance>> captor = ArgumentCaptor.forClass(List.class);
        verify(faceAppearanceRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(FaceAppearance::getFaceId)
                .containsExactly("job-1_1", "job-1_2", "job-1_3");
        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.COMPLETED);
        assertThat(job.getFaceCount()).isEqualTo(3);
        assertThat(store).isEmpty();
    }

    @Test
    void redeliveredLastPartAfterPartialCompletionEndsCompleted() {
        VideoJob job = storedJob(VideoJobStatus.PROCESSING);
        Map<String, JobResultPart> store = partStore();
        service.applyWorkerEvent(embeddingEvent(
                "attempt-x", 1, 2, List.of(face("job-1_1", 1, 0, 0))
        ));
        when(videoJobRepository.save(any(VideoJob.class)))
                .thenThrow(new IllegalStateException("mongo caido"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.applyWorkerEvent(embeddingEvent(
                "attempt-x", 2, 2, List.of(face("job-1_2", 0, 1, 0))
        ))).isInstanceOf(IllegalStateException.class);
        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.COMPLETED);
        assertThat(store).hasSize(2);

        job.setStatus(VideoJobStatus.PROCESSING);
        service.applyWorkerEvent(embeddingEvent(
                "attempt-x", 2, 2, List.of(face("job-1_2", 0, 1, 0))
        ));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.COMPLETED);
        assertThat(job.getFaceCount()).isEqualTo(2);
        assertThat(store).isEmpty();
        verify(faceAppearanceRepository, times(2)).saveAll(anyList());
    }

    @Test
    void completedJobIgnoresLaterParts() {
        VideoJob job = storedJob(VideoJobStatus.COMPLETED);
        Map<String, JobResultPart> store = partStore();

        service.applyWorkerEvent(embeddingEvent(
                "attempt-x", 1, 2, List.of(face("job-1_1", 1, 0, 0))
        ));

        assertThat(store).isEmpty();
        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.COMPLETED);
        verify(videoJobRepository, never()).save(any());
        verify(faceAppearanceRepository, never()).saveAll(anyList());
    }

    @Test
    void partForCompletedJobCleansLeftoverParts() {
        storedJob(VideoJobStatus.COMPLETED);
        Map<String, JobResultPart> store = partStore();
        JobResultPart leftover = new JobResultPart();
        leftover.setId("job-1:attempt-x:1");
        leftover.setJobId("job-1");
        store.put(leftover.getId(), leftover);

        service.applyWorkerEvent(embeddingEvent(
                "attempt-x", 2, 2, List.of(face("job-1_2", 0, 1, 0))
        ));

        assertThat(store).isEmpty();
        verify(jobResultPartRepository).deleteByJobId("job-1");
    }

    @Test
    void failedJobCompletesWhenFullAttemptArrives() {
        VideoJob job = storedJob(VideoJobStatus.FAILED);
        job.setFailureReason("sin respuesta");
        partStore();

        service.applyWorkerEvent(embeddingEvent(
                "attempt-x", 1, 2, List.of(face("job-1_1", 1, 0, 0))
        ));
        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.FAILED);
        service.applyWorkerEvent(embeddingEvent(
                "attempt-x", 2, 2, List.of(face("job-1_2", 0, 1, 0))
        ));

        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.COMPLETED);
        assertThat(job.getFailureReason()).isNull();
    }

    @Test
    void rejectsWrongModel() {
        storedJob(VideoJobStatus.PROCESSING);
        VideoProcessingEvent event = new VideoProcessingEvent(
                VideoProcessingEvent.COMPLETED, "1", "job-1", NOW, null,
                List.of(face("job-1_1", 1, 0, 0)),
                null, null, null, "OtroModelo", 3
        );

        assertThatThrownBy(() -> service.applyWorkerEvent(event))
                .isInstanceOf(InvalidWorkerEventException.class);
        verify(faceAppearanceRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsWrongVectorLength() {
        storedJob(VideoJobStatus.PROCESSING);
        VideoProcessingEvent event = new VideoProcessingEvent(
                VideoProcessingEvent.COMPLETED, "1", "job-1", NOW, null,
                List.of(face("job-1_1", 1, 0, 0)),
                null, null, null, MODEL, 4
        );

        assertThatThrownBy(() -> service.applyWorkerEvent(event))
                .isInstanceOf(InvalidWorkerEventException.class)
                .hasMessageContaining("job-1_1");
        verify(faceAppearanceRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsPartOutOfRange() {
        storedJob(VideoJobStatus.PROCESSING);
        partStore();

        assertThatThrownBy(() -> service.applyWorkerEvent(embeddingEvent(
                "attempt-x", 3, 2, List.of(face("job-1_1", 1, 0, 0))
        ))).isInstanceOf(InvalidWorkerEventException.class);
        assertThatThrownBy(() -> service.applyWorkerEvent(embeddingEvent(
                "attempt-x", 0, 2, List.of(face("job-1_1", 1, 0, 0))
        ))).isInstanceOf(InvalidWorkerEventException.class);
        assertThatThrownBy(() -> service.applyWorkerEvent(embeddingEvent(
                null, 1, 2, List.of(face("job-1_1", 1, 0, 0))
        ))).isInstanceOf(InvalidWorkerEventException.class);
        assertThatThrownBy(() -> service.applyWorkerEvent(embeddingEvent(
                "attempt-x", 2, null, List.of(face("job-1_1", 1, 0, 0))
        ))).isInstanceOf(InvalidWorkerEventException.class);
    }

    @Test
    void rejectsFaceIdInTwoParts() {
        storedJob(VideoJobStatus.PROCESSING);
        Map<String, JobResultPart> store = partStore();
        service.applyWorkerEvent(embeddingEvent(
                "attempt-x", 1, 2, List.of(face("job-1_1", 1, 0, 0))
        ));

        assertThatThrownBy(() -> service.applyWorkerEvent(embeddingEvent(
                "attempt-x", 2, 2, List.of(face("job-1_1", 1, 0, 0))
        ))).isInstanceOf(InvalidWorkerEventException.class)
                .hasMessageContaining("job-1_1");
        assertThat(store).containsOnlyKeys("job-1:attempt-x:1");
    }

    @Test
    void retryDeletesEmbeddingsAndParts() {
        failedJob(VideoJobStatus.FAILED);
        when(videoJobRepository.save(any(VideoJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.retryJob("job-1");

        verify(faceEmbeddingRepository).deleteByJobId("job-1");
        verify(jobResultPartRepository).deleteByJobId("job-1");
    }

    private Map<String, JobResultPart> partStore() {
        Map<String, JobResultPart> store = new LinkedHashMap<>();
        org.mockito.Mockito.lenient().when(jobResultPartRepository.save(any(JobResultPart.class)))
                .thenAnswer(invocation -> {
                    JobResultPart part = invocation.getArgument(0);
                    store.put(part.getId(), part);
                    return part;
                });
        org.mockito.Mockito.lenient().when(jobResultPartRepository.findByJobId("job-1"))
                .thenAnswer(invocation -> new ArrayList<>(store.values()));
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            store.clear();
            return null;
        }).when(jobResultPartRepository).deleteByJobId("job-1");
        return store;
    }

    private FaceAppearanceResult face(String faceId, float x, float y, float z) {
        float[] vector = EmbeddingCodec.normalize(new float[] {x, y, z});
        return new FaceAppearanceResult(
                faceId, "00:00:01", "url", null,
                java.util.Base64.getEncoder().encodeToString(EmbeddingCodec.toBytes(vector))
        );
    }

    private VideoProcessingEvent embeddingEvent(
            String attemptId,
            Integer part,
            Integer parts,
            List<FaceAppearanceResult> appearances
    ) {
        return new VideoProcessingEvent(
                VideoProcessingEvent.COMPLETED, "1", "job-1", NOW, null, appearances,
                attemptId, part, parts, MODEL, 3
        );
    }

    private void useFixedClock(Instant instant) {
        service = new VideoJobService(
                videoJobRepository,
                faceAppearanceRepository,
                faceEmbeddingRepository,
                jobResultPartRepository,
                cameraRepository,
                sqsVideoJobPublisher,
                BUCKET,
                Clock.fixed(instant, ZoneId.of("America/Bogota")),
                UPLOADED_TIMEOUT_MINUTES,
                QUEUED_TIMEOUT_MINUTES,
                PROCESSING_TIMEOUT_MINUTES,
                MODEL,
                faceMatchService
        );
    }

    private VideoJob failedJob(String status) {
        VideoJob job = storedJob(status);
        job.setBucket(BUCKET);
        job.setObjectKey("videos/video.mp4");
        job.setOriginalFileName("video.mp4");
        job.setFailureReason("fallo");
        job.setStartedAt(NOW.minus(2, ChronoUnit.HOURS));
        job.setCompletedAt(NOW.minus(1, ChronoUnit.HOURS));
        job.setFaceCount(3);
        job.setLastWorkerEventAt(NOW.minus(1, ChronoUnit.HOURS));
        return job;
    }

    private void assertBadRequest(VideoUploadedRequest request, String message) {
        assertThatThrownBy(() -> service.registerUploadedVideo(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo(message);
                });
        verify(sqsVideoJobPublisher, never()).publish(any());
        verify(videoJobRepository, never()).save(any());
    }

    private VideoUploadedRequest uploadRequest() {
        return new VideoUploadedRequest("video.mp4", BUCKET, "videos/video.mp4", "CAM-01");
    }

    private VideoJob storedJob(String status) {
        VideoJob job = job("job-1", status);
        when(videoJobRepository.findById("job-1")).thenReturn(Optional.of(job));
        return job;
    }

    private VideoJob job(String id, String status) {
        VideoJob job = new VideoJob();
        job.setId(id);
        job.setCameraId("CAM-01");
        job.setStatus(status);
        job.setCreatedAt(NOW.minus(1, ChronoUnit.DAYS));
        job.setUpdatedAt(NOW.minus(1, ChronoUnit.DAYS));
        return job;
    }

    private VideoProcessingEvent started(Instant sentAt) {
        return new VideoProcessingEvent(
                VideoProcessingEvent.STARTED, "1", "job-1", sentAt, null, null,
                null, null, null, null, null
        );
    }

    private VideoProcessingEvent failed(Instant sentAt, String reason) {
        return new VideoProcessingEvent(
                VideoProcessingEvent.FAILED, "1", "job-1", sentAt, reason, null,
                null, null, null, null, null
        );
    }

    private VideoProcessingEvent completed(
            Instant sentAt,
            List<FaceAppearanceResult> appearances
    ) {
        return new VideoProcessingEvent(
                VideoProcessingEvent.COMPLETED, "1", "job-1", sentAt, null, appearances,
                null, null, null, null, null
        );
    }
}
