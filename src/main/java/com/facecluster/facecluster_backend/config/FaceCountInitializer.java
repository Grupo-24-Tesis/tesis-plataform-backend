package com.facecluster.facecluster_backend.config;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.domain.VideoJob;
import com.facecluster.facecluster_backend.domain.VideoJobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FaceCountInitializer {

    private static final Logger log =
            LoggerFactory.getLogger(FaceCountInitializer.class);

    private final MongoTemplate mongoTemplate;

    public FaceCountInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Order(2)
    @EventListener(ApplicationReadyEvent.class)
    public void fillMissingFaceCounts() {
        try {
            int updated = 0;

            for (VideoJob job : completedJobsWithoutFaceCount()) {
                long faceCount = mongoTemplate.count(
                        new Query(Criteria.where("jobId").is(job.getId())),
                        FaceAppearance.class
                );

                mongoTemplate.updateFirst(
                        new Query(Criteria.where("id").is(job.getId())),
                        new Update().set("faceCount", (int) faceCount),
                        VideoJob.class
                );
                updated++;
            }

            if (updated > 0) {
                log.info("Se completó el faceCount de {} trabajos completados antiguos a partir de sus detecciones", updated);
            }
        } catch (RuntimeException exception) {
            log.error("No se pudo completar el faceCount de los trabajos completados antiguos", exception);
        }
    }

    private List<VideoJob> completedJobsWithoutFaceCount() {
        return mongoTemplate.find(
                new Query(Criteria.where("status").is(VideoJobStatus.COMPLETED).and("faceCount").is(null)),
                VideoJob.class
        );
    }
}
