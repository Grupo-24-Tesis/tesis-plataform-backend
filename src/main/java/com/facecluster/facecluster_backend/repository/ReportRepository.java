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

    List<ReportItem> findByIncidentDateGreaterThanEqual(LocalDateTime start);

    List<ReportItem> findByIncidentDateLessThanEqual(LocalDateTime end);

    List<ReportItem> findByStatusAndIncidentDateGreaterThanEqual(String status, LocalDateTime start);

    List<ReportItem> findByStatusAndIncidentDateLessThanEqual(String status, LocalDateTime end);

    List<ReportItem> findByResolvedAtGreaterThanEqual(LocalDateTime start);

    List<ReportItem> findByResolvedAtLessThanEqual(LocalDateTime end);

    List<ReportItem> findByStatusAndResolvedAtGreaterThanEqual(String status, LocalDateTime start);

    List<ReportItem> findByStatusAndResolvedAtLessThanEqual(String status, LocalDateTime end);
}
