package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.VideoJob;
import com.facecluster.facecluster_backend.domain.VideoJobStatus;
import com.facecluster.facecluster_backend.dto.VideoJobMetrics;
import com.facecluster.facecluster_backend.repository.VideoJobRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Service
public class VideoJobMetricsService {

    private static final Set<String> FAILED_STATUSES = Set.of(
            VideoJobStatus.FAILED,
            VideoJobStatus.QUEUE_ERROR
    );

    private static final Set<String> IN_PROGRESS_STATUSES = Set.of(
            VideoJobStatus.UPLOADED,
            VideoJobStatus.QUEUED,
            VideoJobStatus.PROCESSING
    );

    private final VideoJobRepository videoJobRepository;

    public VideoJobMetricsService(VideoJobRepository videoJobRepository) {
        this.videoJobRepository = videoJobRepository;
    }

    public VideoJobMetrics calculate(String cameraId) {
        List<VideoJob> jobs = cameraId == null || cameraId.isBlank()
                ? videoJobRepository.findAllByOrderByCreatedAtDesc()
                : videoJobRepository.findByCameraIdOrderByCreatedAtDesc(cameraId.trim());

        List<VideoJob> completedJobs = jobs.stream()
                .filter(job -> VideoJobStatus.COMPLETED.equals(job.getStatus()))
                .toList();

        long completed = completedJobs.size();
        long failed = count(jobs, FAILED_STATUSES);
        long inProgress = count(jobs, IN_PROGRESS_STATUSES);
        double failureRate = completed + failed == 0
                ? 0.0
                : (double) failed / (completed + failed);

        List<Integer> faceCounts = completedJobs.stream()
                .map(VideoJob::getFaceCount)
                .toList();

        return new VideoJobMetrics(
                jobs.size(),
                completed,
                failed,
                inProgress,
                failureRate,
                average(jobs, VideoJob::queueWaitSeconds),
                average(jobs, VideoJob::processingSeconds),
                faceCounts.stream().filter(count -> count != null).mapToLong(Integer::longValue).sum(),
                average(completedJobs, VideoJob::getFaceCount)
        );
    }

    private long count(List<VideoJob> jobs, Set<String> statuses) {
        return jobs.stream().filter(job -> statuses.contains(job.getStatus())).count();
    }

    private Double average(List<VideoJob> jobs, Function<VideoJob, ? extends Number> value) {
        return jobs.stream()
                .map(value)
                .filter(number -> number != null)
                .mapToDouble(Number::doubleValue)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
    }
}
