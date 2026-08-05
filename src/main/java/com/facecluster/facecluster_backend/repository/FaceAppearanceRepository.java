package com.facecluster.facecluster_backend.repository;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FaceAppearanceRepository extends MongoRepository<FaceAppearance, String> {
}