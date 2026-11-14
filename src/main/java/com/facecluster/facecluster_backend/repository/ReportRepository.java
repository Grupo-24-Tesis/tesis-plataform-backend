package com.facecluster.facecluster_backend.repository;

import com.facecluster.facecluster_backend.domain.ReportItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportRepository extends MongoRepository<ReportItem, String> {

    List<ReportItem> findByFaceIdsContaining(String faceId);

    List<ReportItem> findByStatus(String status);

    List<ReportItem> findByIncidentDateBetween(
            LocalDateTime start,
            LocalDateTime end
    );

    List<ReportItem> findByStatusAndIncidentDateBetween(
            String status,
            LocalDateTime start,
            LocalDateTime end
    );
    List<ReportItem> findByResolvedAtBetween(
        LocalDateTime start,
        LocalDateTime end
);

List<ReportItem> findByStatusAndResolvedAtBetween(
        String status,
        LocalDateTime start,
        LocalDateTime end
);
}