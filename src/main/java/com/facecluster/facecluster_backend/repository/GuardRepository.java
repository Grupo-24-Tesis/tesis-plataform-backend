package com.facecluster.facecluster_backend.repository;
import com.facecluster.facecluster_backend.domain.Guard;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface GuardRepository extends MongoRepository<Guard, String> {}