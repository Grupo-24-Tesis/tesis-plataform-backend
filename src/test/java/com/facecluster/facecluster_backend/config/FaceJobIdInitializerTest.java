package com.facecluster.facecluster_backend.config;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FaceJobIdInitializerTest {

    private MongoTemplate mongoTemplate;
    private FaceJobIdInitializer initializer;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        initializer = new FaceJobIdInitializer(mongoTemplate);
    }

    @Test
    void buscaSoloLasDeteccionesSinJobId() {
        when(mongoTemplate.find(any(Query.class), eq(FaceAppearance.class))).thenReturn(List.of());

        initializer.fillMissingJobIds();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(FaceAppearance.class));
        assertThat(query.getValue().getQueryObject().toJson()).isEqualTo("{\"jobId\": null}");
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(FaceAppearance.class));
    }

    @Test
    void completaElJobIdConLaParteAnteriorAlUltimoGuionBajo() {
        when(mongoTemplate.find(any(Query.class), eq(FaceAppearance.class))).thenReturn(List.of(
                appearance("a1", "job-1_4"),
                appearance("a2", "job_con_guiones_7")
        ));

        initializer.fillMissingJobIds();

        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(2))
                .updateFirst(queries.capture(), updates.capture(), eq(FaceAppearance.class));

        assertThat(queries.getAllValues().get(0).getQueryObject().toJson()).contains("\"a1\"");
        assertThat(updates.getAllValues().get(0).getUpdateObject().toJson()).contains("\"jobId\": \"job-1\"");
        assertThat(queries.getAllValues().get(1).getQueryObject().toJson()).contains("\"a2\"");
        assertThat(updates.getAllValues().get(1).getUpdateObject().toJson())
                .contains("\"jobId\": \"job_con_guiones\"");
    }

    @Test
    void omiteLasDeteccionesCuyoFaceIdNoTieneJobId() {
        when(mongoTemplate.find(any(Query.class), eq(FaceAppearance.class))).thenReturn(List.of(
                appearance("a1", null),
                appearance("a2", "sinseparador"),
                appearance("a3", "_4")
        ));

        initializer.fillMissingJobIds();

        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(FaceAppearance.class));
    }

    @Test
    void unaFallaNoSePropagaYNoDetieneElArranque() {
        when(mongoTemplate.find(any(Query.class), eq(FaceAppearance.class)))
                .thenThrow(new IllegalStateException("sin conexion"));

        assertThatCode(() -> initializer.fillMissingJobIds()).doesNotThrowAnyException();
    }

    private FaceAppearance appearance(String id, String faceId) {
        FaceAppearance appearance = new FaceAppearance();
        appearance.setId(id);
        appearance.setFaceId(faceId);
        return appearance;
    }
}
