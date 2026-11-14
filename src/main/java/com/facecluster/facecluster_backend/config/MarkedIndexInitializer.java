package com.facecluster.facecluster_backend.config;

import com.facecluster.facecluster_backend.domain.MarkedPerson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class MarkedIndexInitializer {

    private static final Logger log =
            LoggerFactory.getLogger(MarkedIndexInitializer.class);

    private final MongoTemplate mongoTemplate;

    public MarkedIndexInitializer(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureUniqueFaceIdIndex() {
        try {
            int removed = removeDuplicateMarks();

            if (removed > 0) {
                log.warn("Se eliminaron {} marcas duplicadas, se conservó la más antigua de cada detección", removed);
            }

            mongoTemplate.indexOps(MarkedPerson.class)
                    .ensureIndex(new Index().on("faceId", Sort.Direction.ASC).unique());
        } catch (RuntimeException exception) {
            log.error("No se pudo garantizar el índice único de marcas por faceId", exception);
        }
    }

    private int removeDuplicateMarks() {
        List<MarkedPerson> marks = mongoTemplate.find(
                new Query().with(Sort.by(Sort.Direction.ASC, "id")),
                MarkedPerson.class
        );

        Set<String> seenFaceIds = new HashSet<>();
        List<String> duplicateIds = new ArrayList<>();

        for (MarkedPerson mark : marks) {
            if (!seenFaceIds.add(mark.getFaceId())) {
                duplicateIds.add(mark.getId());
            }
        }

        if (!duplicateIds.isEmpty()) {
            mongoTemplate.remove(
                    new Query(Criteria.where("id").in(duplicateIds)),
                    MarkedPerson.class
            );
        }

        return duplicateIds.size();
    }
}
