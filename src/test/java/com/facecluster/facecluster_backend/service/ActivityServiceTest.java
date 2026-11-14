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
import com.facecluster.facecluster_backend.repository.CameraRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityServiceTest {

    private MongoTemplate mongoTemplate;
    private CameraRepository cameraRepository;
    private ActivityService service;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        cameraRepository = mock(CameraRepository.class);
        service = new ActivityService(mongoTemplate, cameraRepository);
    }

    private Document group(int hour, String cameraId, int count) {
        return new Document("_id", new Document("hour", hour).append("cameraId", cameraId))
                .append("count", count);
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
}
