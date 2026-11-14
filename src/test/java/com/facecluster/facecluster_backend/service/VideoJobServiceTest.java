package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.domain.VideoJob;
import com.facecluster.facecluster_backend.domain.VideoJobStatus;
import com.facecluster.facecluster_backend.dto.VideoJobResponse;
import com.facecluster.facecluster_backend.dto.VideoResultRequest;
import com.facecluster.facecluster_backend.dto.VideoResultRequest.FaceAppearanceResult;
import com.facecluster.facecluster_backend.messaging.InvalidWorkerEventException;
import com.facecluster.facecluster_backend.messaging.SqsVideoJobPublisher;
import com.facecluster.facecluster_backend.messaging.VideoProcessingEvent;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.VideoJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoJobServiceTest {

    private static final long QUEUED_TIMEOUT_MINUTES = 720;
    private static final long PROCESSING_TIMEOUT_MINUTES = 15;
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @Mock
    private VideoJobRepository videoJobRepository;

    @Mock
    private FaceAppearanceRepository faceAppearanceRepository;

    @Mock
    private SqsVideoJobPublisher sqsVideoJobPublisher;

    private VideoJobService service;

    @BeforeEach
    void setUp() {
        service = new VideoJobService(
                videoJobRepository,
                faceAppearanceRepository,
                sqsVideoJobPublisher,
                QUEUED_TIMEOUT_MINUTES,
                PROCESSING_TIMEOUT_MINUTES
        );
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
                        "2026-10-16T10:05:21-05:00"
                )
        )));

        ArgumentCaptor<List<FaceAppearance>> saved = ArgumentCaptor.forClass(List.class);
        verify(faceAppearanceRepository).saveAll(saved.capture());
        FaceAppearance appearance = saved.getValue().get(0);
        assertThat(appearance.getFaceId()).isEqualTo("job-1_4");
        assertThat(appearance.getCameraId()).isEqualTo("CAM-01");
        assertThat(appearance.getTimestamp()).isEqualTo("00:01:23");
        assertThat(appearance.getCapturedAt()).hasToString("2026-10-16T10:05:21");
        assertThat(job.getStatus()).isEqualTo(VideoJobStatus.COMPLETED);
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
    void restResultsForUnknownJobAnswerNotFound() {
        when(videoJobRepository.findById("job-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processResults(
                "job-1", new VideoResultRequest(List.of())
        )).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void staleQueuedAndProcessingJobsBecomeFailed() {
        VideoJob queued = job("job-q", VideoJobStatus.QUEUED);
        VideoJob processing = job("job-p", VideoJobStatus.PROCESSING);
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
