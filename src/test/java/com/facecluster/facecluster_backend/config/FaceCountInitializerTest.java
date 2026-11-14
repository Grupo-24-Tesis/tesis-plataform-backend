package com.facecluster.facecluster_backend.config;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.domain.VideoJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FaceCountInitializerTest {

    private MongoTemplate mongoTemplate;
    private FaceCountInitializer initializer;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        initializer = new FaceCountInitializer(mongoTemplate);
    }

    @Test
    void buscaSoloLosTrabajosCompletadosSinFaceCount() {
        when(mongoTemplate.find(any(Query.class), eq(VideoJob.class))).thenReturn(List.of());

        initializer.fillMissingFaceCounts();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(VideoJob.class));
        assertThat(query.getValue().getQueryObject().toJson())
                .isEqualTo("{\"status\": \"COMPLETED\", \"faceCount\": null}");
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(VideoJob.class));
    }

    @Test
    void completaElFaceCountConLasDeteccionesDeCadaTrabajo() {
        when(mongoTemplate.find(any(Query.class), eq(VideoJob.class)))
                .thenReturn(List.of(job("job-1"), job("job-2")));
        when(mongoTemplate.count(argThat(byJobId("job-1")), eq(FaceAppearance.class))).thenReturn(4L);
        when(mongoTemplate.count(argThat(byJobId("job-2")), eq(FaceAppearance.class))).thenReturn(0L);

        initializer.fillMissingFaceCounts();

        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(2))
                .updateFirst(queries.capture(), updates.capture(), eq(VideoJob.class));

        assertThat(queries.getAllValues().get(0).getQueryObject().toJson()).contains("\"job-1\"");
        assertThat(updates.getAllValues().get(0).getUpdateObject().toJson()).contains("\"faceCount\": 4");
        assertThat(queries.getAllValues().get(1).getQueryObject().toJson()).contains("\"job-2\"");
        assertThat(updates.getAllValues().get(1).getUpdateObject().toJson()).contains("\"faceCount\": 0");
    }

    @Test
    void unaFallaNoSePropagaYNoDetieneElArranque() {
        when(mongoTemplate.find(any(Query.class), eq(VideoJob.class)))
                .thenThrow(new IllegalStateException("sin conexion"));

        assertThatCode(() -> initializer.fillMissingFaceCounts()).doesNotThrowAnyException();
    }

    @Test
    void correDespuesDeCompletarElJobIdDeLasDeteccionesAntiguas() throws NoSuchMethodException {
        int jobIdOrder = FaceJobIdInitializer.class
                .getMethod("fillMissingJobIds").getAnnotation(Order.class).value();
        int faceCountOrder = FaceCountInitializer.class
                .getMethod("fillMissingFaceCounts").getAnnotation(Order.class).value();

        assertThat(faceCountOrder).isGreaterThan(jobIdOrder);
    }

    private ArgumentMatcher<Query> byJobId(String jobId) {
        return query -> query != null
                && query.getQueryObject().toJson().equals("{\"jobId\": \"" + jobId + "\"}");
    }

    private VideoJob job(String id) {
        VideoJob job = new VideoJob();
        job.setId(id);
        return job;
    }
}
