package com.facecluster.facecluster_backend.repository;

import com.facecluster.facecluster_backend.domain.ReportItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ReportRepository extends MongoRepository<ReportItem, String> {

    List<ReportItem> findByFaceIdsContaining(String faceId);
}