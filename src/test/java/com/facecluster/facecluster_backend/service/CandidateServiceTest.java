package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.domain.FaceEmbedding;
import com.facecluster.facecluster_backend.dto.MatchDetectionResponse;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.FaceEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateServiceTest {

    private static final String MODEL = "GhostFaceNet";

    private final Map<String, FaceAppearance> appearances = new LinkedHashMap<>();
    private final Map<String, FaceEmbedding> embeddings = new LinkedHashMap<>();
    private FaceEmbeddingRepository embeddingRepository;
    private FaceAppearanceRepository appearanceRepository;
    private FaceMatchService faceMatchService;
    private CandidateService service;

    @BeforeEach
    void setUp() {
        embeddingRepository = mock(FaceEmbeddingRepository.class);
        appearanceRepository = mock(FaceAppearanceRepository.class);
        faceMatchService = mock(FaceMatchService.class);

        when(appearanceRepository.findById(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(appearances.get(invocation.<String>getArgument(0))));
        when(embeddingRepository.findById(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(embeddings.get(invocation.<String>getArgument(0))));
        when(embeddingRepository.findCandidateVectors(anyString(), anyString())).thenAnswer(invocation -> {
            String jobId = invocation.getArgument(1);
            return embeddings.values().stream()
                    .filter(embedding -> !jobId.equals(embedding.getJobId()))
                    .toList();
        });
        when(faceMatchService.decidedWith(anyString())).thenReturn(Set.of());
        when(faceMatchService.detections(anyCollection(), anyMap())).thenAnswer(invocation -> {
            Collection<String> ids = invocation.getArgument(0);
            Map<String, Double> scores = invocation.getArgument(1);
            List<MatchDetectionResponse> responses = new ArrayList<>();
            for (String id : ids) {
                FaceAppearance appearance = appearances.get(id);
                responses.add(new MatchDetectionResponse(
                        id, appearance.getJobId(), null, null, null, null,
                        appearance.getCapturedAt(), scores.get(id), "https://presigned/" + id));
            }
            return responses;
        });

        service = new CandidateService(embeddingRepository, appearanceRepository, faceMatchService, MODEL, 20);

        detection("job-a_1", "job-a", 1, new float[] {1, 0});
    }

    private void detection(String faceId, String jobId, int hour, float[] vector) {
        FaceAppearance appearance = new FaceAppearance();
        appearance.setFaceId(faceId);
        appearance.setJobId(jobId);
        appearance.setCapturedAt(LocalDateTime.parse("2026-11-20T00:00:00").plusHours(hour));
        appearances.put(faceId, appearance);
        embedding(faceId, jobId, vector);
    }

    private void embedding(String faceId, String jobId, float[] vector) {
        FaceEmbedding embedding = new FaceEmbedding();
        embedding.setId(faceId + ":" + MODEL);
        embedding.setFaceId(faceId);
        embedding.setJobId(jobId);
        embedding.setModel(MODEL);
        embedding.setDimensions(vector.length);
        embedding.setVector(EmbeddingCodec.toBytes(vector));
        embeddings.put(embedding.getId(), embedding);
    }

    @Test
    void ranksOtherVideosByScore() {
        detection("job-b_1", "job-b", 2, new float[] {0.6f, 0.8f});
        detection("job-c_1", "job-c", 3, new float[] {0.8f, 0.6f});
        detection("job-d_1", "job-d", 4, new float[] {1, 0});

        List<MatchDetectionResponse> result = service.candidates("job-a_1", null, null);

        assertThat(result).extracting(MatchDetectionResponse::faceId)
                .containsExactly("job-d_1", "job-c_1", "job-b_1");
        assertThat(result.get(0).score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(result.get(1).score()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(result.get(0).frameUrl()).isEqualTo("https://presigned/job-d_1");
    }

    @Test
    void leavesOutSameVideo() {
        detection("job-a_2", "job-a", 5, new float[] {1, 0});
        detection("job-b_1", "job-b", 2, new float[] {0.6f, 0.8f});

        List<MatchDetectionResponse> result = service.candidates("job-a_1", null, null);

        assertThat(result).extracting(MatchDetectionResponse::faceId).containsExactly("job-b_1");
        verify(embeddingRepository).findCandidateVectors(MODEL, "job-a");
    }

    @Test
    void leavesOutDecidedPairs() {
        detection("job-b_1", "job-b", 2, new float[] {0.6f, 0.8f});
        detection("job-c_1", "job-c", 3, new float[] {0.8f, 0.6f});
        when(faceMatchService.decidedWith("job-a_1")).thenReturn(Set.of("job-c_1"));

        List<MatchDetectionResponse> result = service.candidates("job-a_1", null, null);

        assertThat(result).extracting(MatchDetectionResponse::faceId).containsExactly("job-b_1");
    }

    @Test
    void leavesOutConfirmedGroupMembers() {
        detection("job-b_1", "job-b", 2, new float[] {0.6f, 0.8f});
        detection("job-c_1", "job-c", 3, new float[] {0.8f, 0.6f});
        when(faceMatchService.decidedWith("job-a_1")).thenReturn(Set.of("job-b_1"));
        when(faceMatchService.confirmedGroupOf("job-a_1")).thenReturn(Set.of("job-b_1", "job-c_1"));

        List<MatchDetectionResponse> result = service.candidates("job-a_1", null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void noCutOffByDefault() {
        detection("job-b_1", "job-b", 2, new float[] {-0.5f, 0.8660254f});

        List<MatchDetectionResponse> result = service.candidates("job-a_1", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isCloseTo(-0.5, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void minScoreFilters() {
        detection("job-b_1", "job-b", 2, new float[] {0.6f, 0.8f});
        detection("job-c_1", "job-c", 3, new float[] {0.8f, 0.6f});

        List<MatchDetectionResponse> result = service.candidates("job-a_1", null, 0.7);

        assertThat(result).extracting(MatchDetectionResponse::faceId).containsExactly("job-c_1");
    }

    @Test
    void limitKeepsTheBestScores() {
        detection("job-b_1", "job-b", 2, new float[] {0.6f, 0.8f});
        detection("job-c_1", "job-c", 3, new float[] {0.8f, 0.6f});
        detection("job-d_1", "job-d", 4, new float[] {1, 0});

        List<MatchDetectionResponse> result = service.candidates("job-a_1", 2, null);

        assertThat(result).extracting(MatchDetectionResponse::faceId).containsExactly("job-d_1", "job-c_1");
    }

    @Test
    void equalScoresAreOrderedByCapturedAt() {
        detection("job-b_1", "job-b", 9, new float[] {1, 0});
        detection("job-c_1", "job-c", 3, new float[] {1, 0});

        List<MatchDetectionResponse> result = service.candidates("job-a_1", null, null);

        assertThat(result).extracting(MatchDetectionResponse::faceId).containsExactly("job-c_1", "job-b_1");
    }

    @Test
    void candidatesForDetectionWithoutEmbeddingIs409() {
        FaceAppearance bare = new FaceAppearance();
        bare.setFaceId("job-z_1");
        bare.setJobId("job-z");
        appearances.put("job-z_1", bare);

        assertThatThrownBy(() -> service.candidates("job-z_1", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException status = (ResponseStatusException) error;
                    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(status.getReason()).isEqualTo("La detección no tiene embedding");
                });
    }

    @Test
    void unknownDetectionIs404() {
        assertThatThrownBy(() -> service.candidates("nope", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException status = (ResponseStatusException) error;
                    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(status.getReason()).isEqualTo("Detección no encontrada: nope");
                });
    }

    @Test
    void limitOutOfRangeIs400() {
        for (int limit : new int[] {0, 101, -1}) {
            assertThatThrownBy(() -> service.candidates("job-a_1", limit, null))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> {
                        ResponseStatusException status = (ResponseStatusException) error;
                        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(status.getReason()).isEqualTo("limit debe estar entre 1 y 100");
                    });
        }
    }

    @Test
    void minScoreOutOfRangeIs400() {
        assertThatThrownBy(() -> service.candidates("job-a_1", null, 1.5))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> {
                    ResponseStatusException status = (ResponseStatusException) error;
                    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(status.getReason()).isEqualTo("minScore debe estar entre -1 y 1");
                });
    }

    @Test
    void detectionWithoutEmbeddingIsNeverACandidate() {
        detection("job-b_1", "job-b", 2, new float[] {1, 0});
        FaceAppearance bare = new FaceAppearance();
        bare.setFaceId("job-c_1");
        bare.setJobId("job-c");
        appearances.put("job-c_1", bare);

        List<MatchDetectionResponse> result = service.candidates("job-a_1", null, null);

        assertThat(result).extracting(MatchDetectionResponse::faceId).containsExactly("job-b_1");
        verify(faceMatchService).detections(eq(List.of("job-b_1")), anyMap());
    }
}
