package com.facecluster.facecluster_backend.repository;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface FaceAppearanceRepository
        extends MongoRepository<FaceAppearance, String> {

    // Buscar apariciones dentro de un rango de fechas
    List<FaceAppearance> findByCapturedAtBetween(
            LocalDateTime start,
            LocalDateTime end
    );
}