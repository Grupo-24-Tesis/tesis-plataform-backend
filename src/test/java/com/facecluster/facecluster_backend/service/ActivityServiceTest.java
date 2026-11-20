package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.api.DateRangeParams;
import com.facecluster.facecluster_backend.domain.Camera;
import com.facecluster.facecluster_backend.dto.ActivityResponse;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.domain.MarkedPerson;
import com.facecluster.facecluster_backend.domain.ReportItem;
import com.facecluster.facecluster_backend.domain.VideoJob;
import com.facecluster.facecluster_backend.repository.CameraRepository;
import com.facecluster.facecluster_backend.repository.MarkedRepository;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import com.facecluster.facecluster_backend.repository.VideoJobRepository;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityServiceTest {

    private MongoTemplate mongoTemplate;
    private CameraRepository cameraRepository;
    private VideoJobRepository videoJobRepository;
    private MarkedRepository markedRepository;
    private ReportRepository reportRepository;
    private ActivityService service;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        cameraRepository = mock(CameraRepository.class);
        videoJobRepository = mock(VideoJobRepository.class);
        markedRepository = mock(MarkedRepository.class);
        reportRepository = mock(ReportRepository.class);
        service = new ActivityService(
                mongoTemplate, cameraRepository, videoJobRepository, markedRepository, reportRepository);
    }

    private Document group(int hour, String cameraId, int count) {
        return group(hour, cameraId, null, count);
    }

    private Document group(int hour, String cameraId, String jobId, int count) {
        Document key = new Document("hour", hour).append("cameraId", cameraId);
        if (jobId != null) {
            key.append("jobId", jobId);
        }
        return new Document("_id", key).append("count", count);
    }

    private VideoJob job(String id, String fileName, Instant createdAt) {
        VideoJob job = new VideoJob();
        job.setId(id);
        job.setOriginalFileName(fileName);
        job.setCreatedAt(createdAt);
        return job;
    }

    private MarkedPerson mark(String faceId) {
        MarkedPerson mark = new MarkedPerson();
        mark.setFaceId(faceId);
        return mark;
    }

    private ReportItem report(String status, String... faceIds) {
        ReportItem report = new ReportItem();
        report.setStatus(status);
        report.setFaceIds(List.of(faceIds));
        return report;
    }

    private void countedFaces(String... faceIds) {
        when(mongoTemplate.findDistinct(any(Query.class), eq("faceId"), eq(FaceAppearance.class), eq(String.class)))
                .thenReturn(List.of(faceIds));
    }

    @SuppressWarnings("unchecked")
    private void aggregationReturns(List<Document> documents) {
        AggregationResults<Document> results = new AggregationResults<>(documents, new Document());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(FaceAppearance.class), eq(Document.class)))
                .thenReturn(results);
    }

    private Camera camera(String id, String name) {
        Camera camera = new Camera();
        camera.setId(id);
        camera.setName(name);
        return camera;
    }

    @Test
    void fillsTwentyFourHoursWithZeros() {
        aggregationReturns(List.of(group(18, "cam-1", 4), group(3, "cam-1", 1)));
        when(cameraRepository.findAllById(any())).thenReturn(List.of(camera("cam-1", "Entrada")));

        ActivityResponse response = service.calculate(null, DateRangeParams.parse(null, null));

        assertEquals(24, response.hours().size());
        assertEquals(0, response.hours().get(0).hour());
        assertEquals(23, response.hours().get(23).hour());
        assertEquals(4, response.hours().get(18).count());
        assertEquals(1, response.hours().get(3).count());
        assertEquals(0, response.hours().get(10).count());
        assertEquals(5, response.total());
    }

    @Test
    void sumsHoursAcrossCamerasAndSortsCamerasByCountDescending() {
        aggregationReturns(List.of(
                group(8, "cam-1", 2),
                group(9, "cam-2", 7),
                group(9, "cam-1", 1)
        ));
        when(cameraRepository.findAllById(any())).thenReturn(List.of(
                camera("cam-1", "Entrada"),
                camera("cam-2", "Garaje")
        ));

        ActivityResponse response = service.calculate(null, DateRangeParams.parse(null, null));

        assertEquals(8, response.hours().get(9).count());
        assertEquals("Garaje", response.cameras().get(0).cameraName());
        assertEquals(7, response.cameras().get(0).count());
        assertEquals("Entrada", response.cameras().get(1).cameraName());
        assertEquals(3, response.cameras().get(1).count());
        assertEquals(10, response.total());
    }

    @Test
    void usesTheCameraIdAsNameWhenTheCameraDoesNotExist() {
        aggregationReturns(List.of(group(1, "cam-borrada", 2)));
        when(cameraRepository.findAllById(any())).thenReturn(List.of());

        ActivityResponse response = service.calculate(null, DateRangeParams.parse(null, null));

        assertEquals("cam-borrada", response.cameras().get(0).cameraName());
    }

    @Test
    void returnsZeroesWhenThereIsNoActivity() {
        aggregationReturns(List.of());
        when(cameraRepository.findAllById(any())).thenReturn(List.of());

        ActivityResponse response = service.calculate(null, DateRangeParams.parse(null, null));

        assertEquals(24, response.hours().size());
        assertTrue(response.hours().stream().allMatch(hour -> hour.count() == 0));
        assertTrue(response.cameras().isEmpty());
        assertEquals(0, response.total());
    }

    @Test
    void buildsAMatchOnCameraAndRangeAndGroupsByHourInTheStorageZone() {
        aggregationReturns(List.of());
        when(cameraRepository.findAllById(any())).thenReturn(List.of());

        service.calculate("cam-1", DateRangeParams.parse("2026-05-01", "2026-05-31"));

        ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongoTemplate).aggregate(captor.capture(), eq(FaceAppearance.class), eq(Document.class));
        String pipeline = captor.getValue().toString();
        assertTrue(pipeline.contains("$match"));
        assertTrue(pipeline.contains("cam-1"));
        assertTrue(pipeline.contains("$hour"));
        assertTrue(pipeline.contains("timezone"));
        assertTrue(pipeline.contains("$group"));
    }

    @Test
    void countsDetectionsPerVideoAcrossHoursSortedByCount() {
        Instant uploaded = Instant.parse("2026-11-17T16:21:12Z");
        aggregationReturns(List.of(
                group(11, "cam-1", "job-a", 20),
                group(12, "cam-1", "job-a", 6),
                group(11, "cam-1", "job-b", 30),
                group(11, "cam-1", "job-borrado", 1),
                group(11, "cam-1", null, 4)
        ));
        when(videoJobRepository.findAllById(any())).thenReturn(List.of(
                job("job-a", "P1E_S1_C2.mp4", uploaded),
                job("job-b", "Camera_ch6.mp4", uploaded)
        ));

        ActivityResponse response = service.calculate("cam-1", DateRangeParams.parse(null, null));

        assertEquals(3, response.videos().size());
        assertEquals("Camera_ch6.mp4", response.videos().get(0).fileName());
        assertEquals(30, response.videos().get(0).count());
        assertEquals("P1E_S1_C2.mp4", response.videos().get(1).fileName());
        assertEquals(26, response.videos().get(1).count());
        assertEquals(uploaded, response.videos().get(1).uploadedAt());
        assertEquals("job-borrado", response.videos().get(2).fileName());
        assertNull(response.videos().get(2).uploadedAt());
        assertEquals(61, response.total());
        assertEquals(61, response.cameras().get(0).count());
    }

    @Test
    void sortsVideosWithTheSameCountByFileName() {
        aggregationReturns(List.of(
                group(1, "cam-1", "job-z", 5),
                group(1, "cam-1", "job-y", 5)
        ));
        when(videoJobRepository.findAllById(any())).thenReturn(List.of(
                job("job-z", "b.mp4", null),
                job("job-y", "a.mp4", null)
        ));

        ActivityResponse response = service.calculate(null, DateRangeParams.parse(null, null));

        assertEquals(List.of("a.mp4", "b.mp4"),
                response.videos().stream().map(ActivityResponse.VideoCount::fileName).toList());
    }

    @Test
    void splitsTheCountedDetectionsIntoExclusiveFollowUpGroups() {
        aggregationReturns(List.of(group(11, "cam-1", "job-a", 7)));
        countedFaces("f-inv", "f-both", "f-res", "f-res-marked", "f-marked", "f-none", "f-none-2");
        when(markedRepository.findAll()).thenReturn(List.of(
                mark("f-inv"), mark("f-res-marked"), mark("f-marked"), mark("fuera-del-filtro")
        ));
        when(reportRepository.findAll()).thenReturn(List.of(
                report("investigacion", "f-inv", "f-both"),
                report("solucionado", "f-both", "f-res", "f-res-marked", "otra-fuera")
        ));

        ActivityResponse.FollowUp followUp = service.calculate(null, DateRangeParams.parse(null, null)).followUp();

        assertEquals(2, followUp.investigation());
        assertEquals(2, followUp.resolved());
        assertEquals(1, followUp.markedOnly());
        assertEquals(2, followUp.none());
    }

    @Test
    void ignoresReportsWithoutFacesAndEmptyMarks() {
        aggregationReturns(List.of(group(11, "cam-1", "job-a", 2)));
        countedFaces("f-1", "f-2");
        ReportItem empty = new ReportItem();
        empty.setStatus("investigacion");
        when(markedRepository.findAll()).thenReturn(List.of(new MarkedPerson()));
        when(reportRepository.findAll()).thenReturn(List.of(empty));

        ActivityResponse.FollowUp followUp = service.calculate(null, DateRangeParams.parse(null, null)).followUp();

        assertEquals(new ActivityResponse.FollowUp(0, 0, 0, 2), followUp);
    }

    @Test
    void countsFollowUpOverTheSameCameraAndRangeAsTheTotals() {
        aggregationReturns(List.of());

        service.calculate("cam-1", DateRangeParams.parse("2026-05-01", "2026-05-31"));

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findDistinct(captor.capture(), eq("faceId"), eq(FaceAppearance.class), eq(String.class));
        String query = captor.getValue().getQueryObject().toString();
        assertTrue(query.contains("cam-1"));
        assertTrue(query.contains("capturedAt"));
        assertTrue(query.contains("$gte"));
        assertTrue(query.contains("$lte"));
    }

    @Test
    void groupsByJobAsWellAsHourAndCamera() {
        aggregationReturns(List.of());

        service.calculate(null, DateRangeParams.parse(null, null));

        ArgumentCaptor<Aggregation> captor = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongoTemplate).aggregate(captor.capture(), eq(FaceAppearance.class), eq(Document.class));
        assertTrue(captor.getValue().toString().contains("jobId"));
    }
}
