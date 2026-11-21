package com.facecluster.facecluster_backend.repository;

import com.facecluster.facecluster_backend.domain.FaceMatch;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;

public interface FaceMatchRepository extends MongoRepository<FaceMatch, String> {

    @Query("{ 'decision': 'confirmada', '$or': [ { 'faceIdA': { '$in': ?0 } }, { 'faceIdB': { '$in': ?0 } } ] }")
    List<FaceMatch> findConfirmedTouching(Collection<String> faceIds);

    @Query("{ '$or': [ { 'faceIdA': ?0 }, { 'faceIdB': ?0 } ] }")
    List<FaceMatch> findTouching(String faceId);
}
