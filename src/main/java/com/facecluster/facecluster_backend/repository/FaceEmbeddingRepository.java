package com.facecluster.facecluster_backend.repository;

import com.facecluster.facecluster_backend.domain.FaceEmbedding;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface FaceEmbeddingRepository
        extends MongoRepository<FaceEmbedding, String> {

    void deleteByJobId(String jobId);

    List<FaceEmbedding> findByModel(String model);
}
