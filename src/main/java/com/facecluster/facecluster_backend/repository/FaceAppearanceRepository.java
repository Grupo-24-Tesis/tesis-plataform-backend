package com.facecluster.facecluster_backend.repository;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface FaceAppearanceRepository
        extends MongoRepository<FaceAppearance, String> {

    List<FaceAppearance> findByJobIdOrderByTimestampAsc(String jobId);

    List<FaceAppearance> findByCapturedAtBetween(
            LocalDateTime start,
            LocalDateTime end
    );

    List<FaceAppearance> findByCapturedAtGreaterThanEqual(LocalDateTime start);

    List<FaceAppearance> findByCapturedAtLessThanEqual(LocalDateTime end);
}
