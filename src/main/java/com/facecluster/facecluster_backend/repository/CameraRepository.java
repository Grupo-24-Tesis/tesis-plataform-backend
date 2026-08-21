package com.facecluster.facecluster_backend.repository;
import com.facecluster.facecluster_backend.domain.Camera;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface CameraRepository extends MongoRepository<Camera, String> {}