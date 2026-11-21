package com.facecluster.facecluster_backend.repository;

import com.facecluster.facecluster_backend.domain.JobResultPart;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface JobResultPartRepository
        extends MongoRepository<JobResultPart, String> {

    List<JobResultPart> findByJobId(String jobId);

    void deleteByJobId(String jobId);
}
