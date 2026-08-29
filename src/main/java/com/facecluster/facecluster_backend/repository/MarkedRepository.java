package com.facecluster.facecluster_backend.repository;

import com.facecluster.facecluster_backend.domain.MarkedPerson;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MarkedRepository extends MongoRepository<MarkedPerson, String> {

    Optional<MarkedPerson> findByFaceId(String faceId);
}