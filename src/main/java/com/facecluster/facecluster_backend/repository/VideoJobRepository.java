package com.facecluster.facecluster_backend.repository;

import com.facecluster.facecluster_backend.domain.VideoJob;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VideoJobRepository
        extends MongoRepository<VideoJob, String> {
}