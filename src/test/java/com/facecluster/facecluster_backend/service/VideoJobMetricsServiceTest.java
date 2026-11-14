package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.VideoJob;
import com.facecluster.facecluster_backend.domain.VideoJobStatus;
import com.facecluster.facecluster_backend.dto.VideoJobMetrics;
import com.facecluster.facecluster_backend.repository.VideoJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoJobMetricsServiceTest {

    private static final Instant CREATED = Instant.parse("2026-11-14T12:00:00Z");

    private VideoJobRepository repository;
    private VideoJobMetricsService service;

    @BeforeEach
    void setUp() {
        repository = mock(VideoJobRepository.class);
        service = new VideoJobMetricsService(repository);
    }

    private VideoJob job(String status, Long waitSeconds, Long processingSeconds, Integer faces) {
        VideoJob job = new VideoJob();
        job.setStatus(status);
        job.setCreatedAt(CREATED);
        if (waitSeconds != null) {
            job.setStartedAt(CREATED.plusSeconds(waitSeconds));
            if (processingSeconds != null) {
                job.setCompletedAt(job.getStartedAt().plusSeconds(processingSeconds));
            }
        }
        job.setFaceCount(faces);
        return job;
    }

    @Test
    void calculatesRatesAndAveragesSkippingNulls() {
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                job(VideoJobStatus.COMPLETED, 10L, 60L, 4),
                job(VideoJobStatus.COMPLETED, 20L, 100L, 2),
                job(VideoJobStatus.COMPLETED, null, null, null),
                job(VideoJobStatus.FAILED, 30L, null, null),
                job(VideoJobStatus.QUEUE_ERROR, null, null, null),
                job(VideoJobStatus.QUEUED, null, null, null),
                job(VideoJobStatus.PROCESSING, 5L, null, null),
                job(VideoJobStatus.UPLOADED, null, null, null)
        ));

        VideoJobMetrics metrics = service.calculate(null);

        assertThat(metrics.total()).isEqualTo(8);
        assertThat(metrics.completed()).isEqualTo(3);
        assertThat(metrics.failed()).isEqualTo(2);
        assertThat(metrics.inProgress()).isEqualTo(3);
        assertThat(metrics.failureRate()).isEqualTo(0.4);
        assertThat(metrics.averageQueueWaitSeconds()).isEqualTo(16.25);
        assertThat(metrics.averageProcessingSeconds()).isEqualTo(80.0);
        assertThat(metrics.totalFaces()).isEqualTo(6);
        assertThat(metrics.averageFacesPerVideo()).isEqualTo(3.0);
    }

    @Test
    void emptyListGivesZerosAndNullAverages() {
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        VideoJobMetrics metrics = service.calculate("");

        assertThat(metrics.total()).isZero();
        assertThat(metrics.failureRate()).isZero();
        assertThat(metrics.averageQueueWaitSeconds()).isNull();
        assertThat(metrics.averageProcessingSeconds()).isNull();
        assertThat(metrics.averageFacesPerVideo()).isNull();
        assertThat(metrics.totalFaces()).isZero();
    }

    @Test
    void onlyInProgressJobsGiveZeroFailureRate() {
        when(repository.findByCameraIdOrderByCreatedAtDesc("cam-1")).thenReturn(List.of(
                job(VideoJobStatus.QUEUED, null, null, null)
        ));

        VideoJobMetrics metrics = service.calculate(" cam-1 ");

        assertThat(metrics.failureRate()).isZero();
        assertThat(metrics.inProgress()).isEqualTo(1);
        verify(repository).findByCameraIdOrderByCreatedAtDesc("cam-1");
    }
}
