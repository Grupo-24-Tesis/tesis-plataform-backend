package com.facecluster.facecluster_backend.repository;
import com.facecluster.facecluster_backend.domain.MarkedPerson;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface MarkedRepository extends MongoRepository<MarkedPerson, String> {}