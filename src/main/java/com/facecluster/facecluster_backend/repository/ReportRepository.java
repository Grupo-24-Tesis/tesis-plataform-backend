package com.facecluster.facecluster_backend.repository;

import com.facecluster.facecluster_backend.domain.ReportItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportRepository extends MongoRepository<ReportItem, String> {

    // Buscar reportes relacionados con una cara
    List<ReportItem> findByFaceIdsContaining(String faceId);

    // Filtrar por estado
    List<ReportItem> findByStatus(String status);

    // Filtrar por rango de fecha del incidente
    List<ReportItem> findByIncidentDateBetween(
            LocalDateTime start,
            LocalDateTime end
    );

    // Filtrar por estado + rango de fecha
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