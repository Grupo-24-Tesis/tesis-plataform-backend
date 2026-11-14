package com.facecluster.facecluster_backend.repository;

import com.facecluster.facecluster_backend.domain.VideoJob;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface VideoJobRepository
        extends MongoRepository<VideoJob, String> {

    List<VideoJob> findAllByOrderByCreatedAtDesc();

    List<VideoJob> findByCameraIdOrderByCreatedAtDesc(String cameraId);

    List<VideoJob> findByStatusAndUpdatedAtBefore(
            String status,
            Instant updatedAt
    );
}
