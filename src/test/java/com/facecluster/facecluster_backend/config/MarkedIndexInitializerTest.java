package com.facecluster.facecluster_backend.config;

import com.facecluster.facecluster_backend.domain.MarkedPerson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarkedIndexInitializerTest {

    private MongoTemplate mongoTemplate;
    private IndexOperations indexOperations;
    private MarkedIndexInitializer initializer;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        indexOperations = mock(IndexOperations.class);
        when(mongoTemplate.indexOps(MarkedPerson.class)).thenReturn(indexOperations);
        initializer = new MarkedIndexInitializer(mongoTemplate);
    }

    @Test
    void eliminaDuplicadosConservandoLaMasAntiguaYLuegoCreaElIndiceUnico() {
        when(mongoTemplate.find(any(Query.class), eq(MarkedPerson.class))).thenReturn(List.of(
                mark("1", "job-1_4"),
                mark("2", "job-1_7"),
                mark("3", "job-1_4"),
                mark("4", "job-1_4")
        ));

        initializer.ensureUniqueFaceIdIndex();

        ArgumentCaptor<Query> removed = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).remove(removed.capture(), eq(MarkedPerson.class));
        assertThat(removed.getValue().getQueryObject().toJson())
                .contains("\"3\"")
                .contains("\"4\"")
                .doesNotContain("\"1\"")
                .doesNotContain("\"2\"");

        ArgumentCaptor<IndexDefinition> index = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations).ensureIndex(index.capture());
        assertThat(index.getValue().getIndexKeys().toJson()).contains("faceId");
        assertThat(index.getValue().getIndexOptions().getBoolean("unique")).isTrue();
    }

    @Test
    void sinDuplicadosSoloCreaElIndiceUnico() {
        when(mongoTemplate.find(any(Query.class), eq(MarkedPerson.class))).thenReturn(List.of(
                mark("1", "job-1_4"),
                mark("2", "job-1_7")
        ));

        initializer.ensureUniqueFaceIdIndex();

        verify(mongoTemplate, never()).remove(any(Query.class), eq(MarkedPerson.class));
        verify(indexOperations).ensureIndex(any(IndexDefinition.class));
    }

    @Test
    void unaFallaNoSePropagaYNoDetieneElArranque() {
        when(mongoTemplate.find(any(Query.class), eq(MarkedPerson.class)))
                .thenThrow(new IllegalStateException("sin conexion"));

        assertThatCode(() -> initializer.ensureUniqueFaceIdIndex()).doesNotThrowAnyException();
    }

    @Test
    void unaFallaAlCrearElIndiceNoSePropaga() {
        when(mongoTemplate.find(any(Query.class), eq(MarkedPerson.class))).thenReturn(List.of());
        when(indexOperations.ensureIndex(any(IndexDefinition.class)))
                .thenThrow(new IllegalStateException("indice en conflicto"));

        assertThatCode(() -> initializer.ensureUniqueFaceIdIndex()).doesNotThrowAnyException();
    }

    private MarkedPerson mark(String id, String faceId) {
        MarkedPerson mark = new MarkedPerson();
        mark.setId(id);
        mark.setFaceId(faceId);
        return mark;
    }
}
