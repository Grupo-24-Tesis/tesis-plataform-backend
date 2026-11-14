package com.facecluster.facecluster_backend.config;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FaceJobIdInitializer {

    private static final Logger log =
            LoggerFactory.getLogger(FaceJobIdInitializer.class);

    private final MongoTemplate mongoTemplate;

    public FaceJobIdInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void fillMissingJobIds() {
        try {
            int updated = 0;

            for (FaceAppearance appearance : appearancesWithoutJobId()) {
                String jobId = jobIdOf(appearance.getFaceId());

                if (jobId == null) {
                    continue;
                }

                mongoTemplate.updateFirst(
                        new Query(Criteria.where("id").is(appearance.getId())),
                        new Update().set("jobId", jobId),
                        FaceAppearance.class
                );
                updated++;
            }

            if (updated > 0) {
                log.info("Se completó el jobId de {} detecciones antiguas a partir de su faceId", updated);
            }
        } catch (RuntimeException exception) {
            log.error("No se pudo completar el jobId de las detecciones antiguas", exception);
        }
    }

    private List<FaceAppearance> appearancesWithoutJobId() {
        return mongoTemplate.find(
                new Query(Criteria.where("jobId").is(null)),
                FaceAppearance.class
        );
    }

    private String jobIdOf(String faceId) {
        if (faceId == null) {
            return null;
        }

        int separator = faceId.lastIndexOf('_');
        return separator > 0 ? faceId.substring(0, separator) : null;
    }
}
