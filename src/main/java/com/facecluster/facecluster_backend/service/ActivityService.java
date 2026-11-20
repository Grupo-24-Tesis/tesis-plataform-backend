package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.api.DateRangeParams.DateRange;
import com.facecluster.facecluster_backend.domain.Camera;
import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.domain.MarkedPerson;
import com.facecluster.facecluster_backend.domain.ReportItem;
import com.facecluster.facecluster_backend.domain.VideoJob;
import com.facecluster.facecluster_backend.dto.ActivityResponse;
import com.facecluster.facecluster_backend.dto.ActivityResponse.CameraCount;
import com.facecluster.facecluster_backend.dto.ActivityResponse.FollowUp;
import com.facecluster.facecluster_backend.dto.ActivityResponse.HourCount;
import com.facecluster.facecluster_backend.dto.ActivityResponse.VideoCount;
import com.facecluster.facecluster_backend.repository.CameraRepository;
import com.facecluster.facecluster_backend.repository.MarkedRepository;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import com.facecluster.facecluster_backend.repository.VideoJobRepository;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

@Service
public class ActivityService {

    private static final int HOURS_PER_DAY = 24;
    private static final String STATUS_INVESTIGATION = "investigacion";
    private static final String STATUS_RESOLVED = "solucionado";

    private final MongoTemplate mongoTemplate;
    private final CameraRepository cameraRepository;
    private final VideoJobRepository videoJobRepository;
    private final MarkedRepository markedRepository;
    private final ReportRepository reportRepository;

    public ActivityService(
            MongoTemplate mongoTemplate,
            CameraRepository cameraRepository,
            VideoJobRepository videoJobRepository,
            MarkedRepository markedRepository,
            ReportRepository reportRepository
    ) {
        this.mongoTemplate = mongoTemplate;
        this.cameraRepository = cameraRepository;
        this.videoJobRepository = videoJobRepository;
        this.markedRepository = markedRepository;
        this.reportRepository = reportRepository;
    }

    public ActivityResponse calculate(String cameraId, DateRange range) {
        Criteria criteria = matchCriteria(cameraId, range);
        List<Document> groups = mongoTemplate
                .aggregate(buildAggregation(criteria), FaceAppearance.class, Document.class)
                .getMappedResults();
        List<String> faceIds = mongoTemplate.findDistinct(
                Query.query(criteria), "faceId", FaceAppearance.class, String.class);
        return toResponse(groups, followUp(faceIds));
    }

    private Criteria matchCriteria(String cameraId, DateRange range) {
        Criteria captured = Criteria.where("capturedAt").ne(null);
        if (range.hasFrom()) {
            captured = captured.gte(range.from());
        }
        if (range.hasTo()) {
            captured = captured.lte(range.to());
        }

        List<Criteria> filters = new ArrayList<>();
        filters.add(captured);
        if (cameraId != null && !cameraId.isBlank()) {
            filters.add(Criteria.where("cameraId").is(cameraId.trim()));
        }
        return new Criteria().andOperator(filters);
    }

    private Aggregation buildAggregation(Criteria criteria) {
        DateOperators.Hour hour = DateOperators
                .dateOf("capturedAt")
                .withTimezone(DateOperators.Timezone.valueOf(ZoneId.systemDefault().getId()))
                .hour();

        return Aggregation.newAggregation(
                FaceAppearance.class,
                Aggregation.match(criteria),
                Aggregation.project("cameraId", "jobId").and(hour).as("hour"),
                Aggregation.group("hour", "cameraId", "jobId").count().as("count")
        );
    }

    private FollowUp followUp(List<String> faceIds) {
        Set<String> counted = new HashSet<>();
        for (String faceId : faceIds) {
            if (faceId != null) {
                counted.add(faceId);
            }
        }

        Set<String> marked = new HashSet<>();
        for (MarkedPerson mark : markedRepository.findAll()) {
            if (mark.getFaceId() != null) {
                marked.add(mark.getFaceId());
            }
        }

        Set<String> inInvestigation = new HashSet<>();
        Set<String> inResolved = new HashSet<>();
        for (ReportItem report : reportRepository.findAll()) {
            if (report.getFaceIds() == null) {
                continue;
            }
            Set<String> target = STATUS_INVESTIGATION.equals(report.getStatus())
                    ? inInvestigation
                    : STATUS_RESOLVED.equals(report.getStatus()) ? inResolved : null;
            if (target == null) {
                continue;
            }
            report.getFaceIds().stream().filter(Objects::nonNull).forEach(target::add);
        }

        long investigation = 0;
        long resolved = 0;
        long markedOnly = 0;
        long none = 0;
        for (String faceId : counted) {
            if (inInvestigation.contains(faceId)) {
                investigation++;
            } else if (inResolved.contains(faceId)) {
                resolved++;
            } else if (marked.contains(faceId)) {
                markedOnly++;
            } else {
                none++;
            }
        }
        return new FollowUp(investigation, resolved, markedOnly, none);
    }

    private ActivityResponse toResponse(List<Document> groups, FollowUp followUp) {
        long[] byHour = new long[HOURS_PER_DAY];
        Map<String, Long> byCamera = new HashMap<>();
        Map<String, Long> byJob = new HashMap<>();
        long total = 0;

        for (Document group : groups) {
            Document key = group.get("_id", Document.class);
            Number hour = key.get("hour", Number.class);
            Number count = group.get("count", Number.class);
            Object cameraId = key.get("cameraId");
            Object jobId = key.get("jobId");
            if (cameraId == null || hour == null || count == null || hour.intValue() < 0 || hour.intValue() >= HOURS_PER_DAY) {
                continue;
            }
            byHour[hour.intValue()] += count.longValue();
            byCamera.merge(cameraId.toString(), count.longValue(), Long::sum);
            if (jobId != null) {
                byJob.merge(jobId.toString(), count.longValue(), Long::sum);
            }
            total += count.longValue();
        }

        List<HourCount> hours = IntStream.range(0, HOURS_PER_DAY)
                .mapToObj(hour -> new HourCount(hour, byHour[hour]))
                .toList();

        return new ActivityResponse(hours, cameraCounts(byCamera), videoCounts(byJob), followUp, total);
    }

    private List<CameraCount> cameraCounts(Map<String, Long> byCamera) {
        Map<String, String> names = new HashMap<>();
        for (Camera camera : cameraRepository.findAllById(byCamera.keySet())) {
            if (camera.getName() != null && !camera.getName().isBlank()) {
                names.put(camera.getId(), camera.getName());
            }
        }

        return byCamera.entrySet().stream()
                .map(entry -> new CameraCount(
                        entry.getKey(),
                        names.getOrDefault(entry.getKey(), entry.getKey()),
                        entry.getValue()))
                .sorted(Comparator.comparingLong(CameraCount::count).reversed()
                        .thenComparing(CameraCount::cameraId))
                .toList();
    }

    private List<VideoCount> videoCounts(Map<String, Long> byJob) {
        Map<String, VideoJob> jobs = new HashMap<>();
        if (!byJob.isEmpty()) {
            for (VideoJob job : videoJobRepository.findAllById(byJob.keySet())) {
                jobs.put(job.getId(), job);
            }
        }

        return byJob.entrySet().stream()
                .map(entry -> {
                    VideoJob job = jobs.get(entry.getKey());
                    String fileName = job != null && job.getOriginalFileName() != null && !job.getOriginalFileName().isBlank()
                            ? job.getOriginalFileName()
                            : entry.getKey();
                    return new VideoCount(
                            entry.getKey(),
                            fileName,
                            job == null ? null : job.getCreatedAt(),
                            entry.getValue());
                })
                .sorted(Comparator.comparingLong(VideoCount::count).reversed()
                        .thenComparing(VideoCount::fileName)
                        .thenComparing(VideoCount::jobId))
                .toList();
    }
}
