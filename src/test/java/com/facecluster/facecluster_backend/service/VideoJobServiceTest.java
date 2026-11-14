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
import com.facecluster.facecluster_backend.repository.CameraRepository;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.VideoJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoJobServiceTest {

    private static final String BUCKET = "bucket";
    private static final long UPLOADED_TIMEOUT_MINUTES = 5;
    private static final long QUEUED_TIMEOUT_MINUTES = 720;
    private static final long PROCESSING_TIMEOUT_MINUTES = 15;
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @Mock
    private VideoJobRepository videoJobRepository;

    @Mock
    private FaceAppearanceRepository faceAppearanceRepository;

    @Mock
    private CameraRepository cameraRepository;

    @Mock
    private SqsVideoJobPublisher sqsVideoJobPublisher;

    private VideoJobService service;

    @BeforeEach
    void setUp() {
        service = new VideoJobService(
                videoJobRepository,
                faceAppearanceRepository,
                cameraRepository,
                sqsVideoJobPublisher,
                BUCKET,
                Clock.system(ZoneId.of("America/Bogota")),
                UPLOADED_TIMEOUT_MINUTES,
                QUEUED_TIMEOUT_MINUTES,
                PROCESSING_TIMEOUT_MINUTES
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
                "OTRO", "1", "job-1", NOW, null, null
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
        VideoJob job = storedJob(VideoJobStatus.PROCESSING);
        Instant before = Instant.now();

        service.applyWorkerEvent(completed(null, List.of()));

        assertThat(job.getCompletedAt()).isBetween(before, Instant.now());
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
                VideoProcessingEvent.STARTED, "1", "job-1", sentAt, null, null
        );
    }

    private VideoProcessingEvent failed(Instant sentAt, String reason) {
        return new VideoProcessingEvent(
                VideoProcessingEvent.FAILED, "1", "job-1", sentAt, reason, null
        );
    }

    private VideoProcessingEvent completed(
            Instant sentAt,
            List<FaceAppearanceResult> appearances
    ) {
        return new VideoProcessingEvent(
                VideoProcessingEvent.COMPLETED, "1", "job-1", sentAt, null, appearances
        );
    }
}
