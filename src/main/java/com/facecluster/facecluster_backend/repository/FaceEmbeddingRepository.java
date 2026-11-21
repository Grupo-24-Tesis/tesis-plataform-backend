package com.facecluster.facecluster_backend.repository;

import com.facecluster.facecluster_backend.domain.FaceEmbedding;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface FaceEmbeddingRepository
        extends MongoRepository<FaceEmbedding, String> {

    void deleteByJobId(String jobId);

    List<FaceEmbedding> findByModel(String model);

    @Query(value = "{ 'model': ?0, 'jobId': { $ne: ?1 } }",
            fields = "{ 'faceId': 1, 'jobId': 1, 'vector': 1 }")
    List<FaceEmbedding> findCandidateVectors(String model, String jobId);
}
