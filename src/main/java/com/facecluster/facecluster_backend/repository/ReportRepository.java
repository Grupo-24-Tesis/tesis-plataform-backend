package com.facecluster.facecluster_backend.repository;
import com.facecluster.facecluster_backend.domain.ReportItem;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface ReportRepository extends MongoRepository<ReportItem, String> {}