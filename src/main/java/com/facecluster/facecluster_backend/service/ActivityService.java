package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.api.DateRangeParams.DateRange;
import com.facecluster.facecluster_backend.domain.Camera;
import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.dto.ActivityResponse;
import com.facecluster.facecluster_backend.dto.ActivityResponse.CameraCount;
import com.facecluster.facecluster_backend.dto.ActivityResponse.HourCount;
import com.facecluster.facecluster_backend.repository.CameraRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
public class ActivityService {

    private static final int HOURS_PER_DAY = 24;

    private final MongoTemplate mongoTemplate;
    private final CameraRepository cameraRepository;

    public ActivityService(MongoTemplate mongoTemplate, CameraRepository cameraRepository) {
        this.mongoTemplate = mongoTemplate;
        this.cameraRepository = cameraRepository;
    }

    public ActivityResponse calculate(String cameraId, DateRange range) {
        List<Document> groups = mongoTemplate
                .aggregate(buildAggregation(cameraId, range), FaceAppearance.class, Document.class)
                .getMappedResults();
        return toResponse(groups);
    }

    private Aggregation buildAggregation(String cameraId, DateRange range) {
        Criteria criteria = Criteria.where("capturedAt").ne(null);
        if (range.hasFrom()) {
            criteria = criteria.gte(range.from());
        }
        if (range.hasTo()) {
            criteria = criteria.lte(range.to());
        }

        List<Criteria> filters = new ArrayList<>();
        filters.add(criteria);
        if (cameraId != null && !cameraId.isBlank()) {
            filters.add(Criteria.where("cameraId").is(cameraId.trim()));
        }

        DateOperators.Hour hour = DateOperators
                .dateOf("capturedAt")
                .withTimezone(DateOperators.Timezone.valueOf(ZoneId.systemDefault().getId()))
                .hour();

        return Aggregation.newAggregation(
                FaceAppearance.class,
                Aggregation.match(new Criteria().andOperator(filters)),
                Aggregation.project("cameraId").and(hour).as("hour"),
                Aggregation.group("hour", "cameraId").count().as("count")
        );
    }

    private ActivityResponse toResponse(List<Document> groups) {
        long[] byHour = new long[HOURS_PER_DAY];
        Map<String, Long> byCamera = new HashMap<>();
        long total = 0;

        for (Document group : groups) {
            Document key = group.get("_id", Document.class);
            Number hour = key.get("hour", Number.class);
            Number count = group.get("count", Number.class);
            Object cameraId = key.get("cameraId");
            if (cameraId == null || hour == null || count == null || hour.intValue() < 0 || hour.intValue() >= HOURS_PER_DAY) {
                continue;
            }
            byHour[hour.intValue()] += count.longValue();
            byCamera.merge(cameraId.toString(), count.longValue(), Long::sum);
            total += count.longValue();
        }

        List<HourCount> hours = IntStream.range(0, HOURS_PER_DAY)
                .mapToObj(hour -> new HourCount(hour, byHour[hour]))
                .toList();

        Map<String, String> names = new HashMap<>();
        for (Camera camera : cameraRepository.findAllById(byCamera.keySet())) {
            if (camera.getName() != null && !camera.getName().isBlank()) {
                names.put(camera.getId(), camera.getName());
            }
        }

        List<CameraCount> cameras = byCamera.entrySet().stream()
                .map(entry -> new CameraCount(
                        entry.getKey(),
                        names.getOrDefault(entry.getKey(), entry.getKey()),
                        entry.getValue()))
                .sorted(Comparator.comparingLong(CameraCount::count).reversed()
                        .thenComparing(CameraCount::cameraId))
                .toList();

        return new ActivityResponse(hours, cameras, total);
    }
}
