package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.Camera;
import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.domain.FaceMatch;
import com.facecluster.facecluster_backend.domain.VideoJob;
import com.facecluster.facecluster_backend.dto.DetectionMatchesResponse;
import com.facecluster.facecluster_backend.dto.FaceMatchRequest;
import com.facecluster.facecluster_backend.repository.CameraRepository;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.FaceMatchRepository;
import com.facecluster.facecluster_backend.repository.GuardRepository;
import com.facecluster.facecluster_backend.repository.VideoJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FaceMatchServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    private final Map<String, FaceMatch> store = new LinkedHashMap<>();
    private final Map<String, FaceAppearance> appearances = new LinkedHashMap<>();
    private FaceMatchRepository matchRepository;
    private FaceAppearanceRepository appearanceRepository;
    private VideoJobRepository videoJobRepository;
    private CameraRepository cameraRepository;
    private GuardRepository guardRepository;
    private FaceImageService faceImageService;
    private FaceMatchService service;

    @BeforeEach
    void setUp() {
        matchRepository = mock(FaceMatchRepository.class);
        appearanceRepository = mock(FaceAppearanceRepository.class);
        videoJobRepository = mock(VideoJobRepository.class);
        cameraRepository = mock(CameraRepository.class);
        guardRepository = mock(GuardRepository.class);
        faceImageService = mock(FaceImageService.class);

        when(matchRepository.findById(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(store.get(invocation.<String>getArgument(0))));
        when(matchRepository.existsById(anyString()))
                .thenAnswer(invocation -> store.containsKey(invocation.<String>getArgument(0)));
        when(matchRepository.save(any(FaceMatch.class))).thenAnswer(invocation -> {
            FaceMatch match = invocation.getArgument(0);
            store.put(match.getId(), match);
            return match;
        });
        when(matchRepository.findConfirmedTouching(anyCollection())).thenAnswer(invocation -> {
            Collection<String> ids = invocation.getArgument(0);
            return store.values().stream()
                    .filter(match -> "confirmada".equals(match.getDecision()))
                    .filter(match -> ids.contains(match.getFaceIdA()) || ids.contains(match.getFaceIdB()))
                    .toList();
        });
        when(matchRepository.findTouching(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return store.values().stream()
                    .filter(match -> id.equals(match.getFaceIdA()) || id.equals(match.getFaceIdB()))
                    .toList();
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            store.remove(invocation.<String>getArgument(0));
            return null;
        }).when(matchRepository).deleteById(anyString());

        when(appearanceRepository.findById(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(appearances.get(invocation.<String>getArgument(0))));
        when(appearanceRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<String> ids = invocation.getArgument(0);
            List<FaceAppearance> found = new java.util.ArrayList<>();
            for (String id : ids) {
                if (appearances.containsKey(id)) {
                    found.add(appearances.get(id));
                }
            }
            return found;
        });
        when(faceImageService.withS3ImagesByJob(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(guardRepository.existsById(anyString())).thenReturn(true);

        Camera camera = new Camera();
        camera.setId("cam-1");
        camera.setName("Entrada");
        when(cameraRepository.findAllById(any())).thenReturn(List.of(camera));

        List<VideoJob> jobs = List.of(job("job-a", "a.mp4"), job("job-b", "b.mp4"), job("job-c", "c.mp4"), job("job-d", "d.mp4"));
        when(videoJobRepository.findAllById(any())).thenReturn(jobs);

        service = new FaceMatchService(
                matchRepository, appearanceRepository, videoJobRepository, cameraRepository,
                guardRepository, faceImageService,
                Clock.fixed(NOW, ZoneId.of("America/Bogota")), "GhostFaceNet"
        );

        appearance("job-a_1", "job-a", 1);
        appearance("job-b_1", "job-b", 2);
        appearance("job-c_1", "job-c", 3);
        appearance("job-d_1", "job-d", 4);
    }

    private VideoJob job(String id, String name) {
        VideoJob job = new VideoJob();
        job.setId(id);
        job.setOriginalFileName(name);
        return job;
    }

    private void appearance(String faceId, String jobId, int hour) {
        FaceAppearance appearance = new FaceAppearance();
        appearance.setFaceId(faceId);
        appearance.setId(faceId);
        appearance.setJobId(jobId);
        appearance.setCameraId("cam-1");
        appearance.setTimestamp("00:00:10");
        appearance.setCapturedAt(LocalDateTime.of(2026, 11, 20, hour, 0));
        appearance.setFrameUrl("url-" + faceId);
        appearances.put(faceId, appearance);
    }

    private FaceMatchRequest request(String a, String b, String decision) {
        return new FaceMatchRequest(a, b, decision, 0.8, null);
    }

    private ResponseStatusException failure(Runnable action) {
        try {
            action.run();
        } catch (ResponseStatusException e) {
            return e;
        }
        throw new AssertionError("Se esperaba ResponseStatusException");
    }

    @Test
    void pairIdIsOrderIndependent() {
        assertThat(FaceMatchService.pairId("job-b_1", "job-a_1")).isEqualTo("job-a_1|job-b_1");
        assertThat(FaceMatchService.pairId("job-a_1", "job-b_1")).isEqualTo("job-a_1|job-b_1");
    }

    @Test
    void samePairFromEitherSideIsOneDocument() {
        FaceMatchService.DecisionResult first = service.decide(request("job-a_1", "job-b_1", "confirmada"));
        FaceMatchService.DecisionResult second = service.decide(request("job-b_1", "job-a_1", "confirmada"));
        FaceMatchService.DecisionResult third = service.decide(request("job-a_1", "job-b_1", "confirmada"));

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(third.created()).isFalse();
        assertThat(store).hasSize(1);
        assertThat(store.get("job-a_1|job-b_1").getFaceIdA()).isEqualTo("job-a_1");
        assertThat(store.get("job-a_1|job-b_1").getFaceIdB()).isEqualTo("job-b_1");
    }

    @Test
    void changingDecisionOverwrites() {
        service.decide(request("job-a_1", "job-b_1", "confirmada"));
        FaceMatchService.DecisionResult changed = service.decide(request("job-a_1", "job-b_1", "descartada"));

        assertThat(changed.created()).isFalse();
        assertThat(store).hasSize(1);
        assertThat(store.get("job-a_1|job-b_1").getDecision()).isEqualTo("descartada");
        assertThat(store.get("job-a_1|job-b_1").getModel()).isEqualTo("GhostFaceNet");
        assertThat(store.get("job-a_1|job-b_1").getUpdatedAt()).isNotNull();
    }

    @Test
    void rejectsSameDetection() {
        ResponseStatusException e = failure(() -> service.decide(request("job-a_1", "job-a_1", "confirmada")));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).isEqualTo("Una detección no puede coincidir consigo misma");
    }

    @Test
    void rejectsSameVideo() {
        appearance("job-a_2", "job-a", 5);
        ResponseStatusException e = failure(() -> service.decide(request("job-a_1", "job-a_2", "confirmada")));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).isEqualTo("Las dos detecciones son del mismo video");
    }

    @Test
    void rejectsUnknownDetection() {
        ResponseStatusException e = failure(() -> service.decide(request("job-a_1", "job-z_9", "confirmada")));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(e.getReason()).isEqualTo("Detección no encontrada: job-z_9");
    }

    @Test
    void rejectsInvalidDecision() {
        ResponseStatusException e = failure(() -> service.decide(request("job-a_1", "job-b_1", "quizas")));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).isEqualTo("Decisión inválida: use confirmada o descartada");
    }

    @Test
    void rejectsUnknownGuard() {
        when(guardRepository.existsById("g-x")).thenReturn(false);
        FaceMatchRequest body = new FaceMatchRequest("job-a_1", "job-b_1", "confirmada", 0.5, "g-x");
        ResponseStatusException e = failure(() -> service.decide(body));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).isEqualTo("El vigilante no existe");
        assertThat(store).isEmpty();
    }

    @Test
    void groupIsTransitive() {
        service.decide(request("job-a_1", "job-b_1", "confirmada"));
        service.decide(request("job-b_1", "job-c_1", "confirmada"));
        service.decide(request("job-c_1", "job-d_1", "descartada"));

        DetectionMatchesResponse group = service.group("job-a_1");

        assertThat(group.faceId()).isEqualTo("job-a_1");
        assertThat(group.detections()).extracting(d -> d.faceId())
                .containsExactly("job-b_1", "job-c_1");
        assertThat(group.links()).extracting(link -> link.id())
                .containsExactlyInAnyOrder("job-a_1|job-b_1", "job-b_1|job-c_1");
        assertThat(group.detections().get(0).cameraName()).isEqualTo("Entrada");
        assertThat(group.detections().get(0).videoName()).isEqualTo("b.mp4");
        assertThat(group.detections().get(0).score()).isNull();
        assertThat(group.detections().get(0).frameUrl()).isEqualTo("url-job-b_1");
    }

    @Test
    void groupListsDirectDiscards() {
        service.decide(request("job-a_1", "job-b_1", "confirmada"));
        service.decide(request("job-a_1", "job-c_1", "descartada"));
        service.decide(request("job-d_1", "job-a_1", "descartada"));
        service.decide(request("job-b_1", "job-c_1", "descartada"));
        store.get("job-a_1|job-c_1").setUpdatedAt(LocalDateTime.of(2026, 11, 20, 8, 0));
        store.get("job-a_1|job-d_1").setUpdatedAt(LocalDateTime.of(2026, 11, 20, 9, 0));

        DetectionMatchesResponse group = service.group("job-a_1");

        assertThat(group.discarded()).extracting(d -> d.id())
                .containsExactly("job-a_1|job-d_1", "job-a_1|job-c_1");
        assertThat(group.discarded()).extracting(d -> d.detection().faceId())
                .containsExactly("job-d_1", "job-c_1");
        assertThat(group.discarded().get(0).detection().score()).isNull();
        assertThat(group.detections()).extracting(d -> d.faceId()).containsExactly("job-b_1");
    }

    @Test
    void confirmedGroupOfListsEveryMember() {
        service.decide(request("job-a_1", "job-b_1", "confirmada"));
        service.decide(request("job-b_1", "job-c_1", "confirmada"));
        service.decide(request("job-c_1", "job-d_1", "descartada"));

        assertThat(service.confirmedGroupOf("job-a_1")).containsExactlyInAnyOrder("job-b_1", "job-c_1");
    }

    @Test
    void groupOfUnknownDetectionIs404() {
        ResponseStatusException e = failure(() -> service.group("job-z_9"));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(e.getReason()).isEqualTo("Detección no encontrada: job-z_9");
    }

    @Test
    void undoingMiddleLinkSplitsGroup() {
        service.decide(request("job-a_1", "job-b_1", "confirmada"));
        service.decide(request("job-b_1", "job-c_1", "confirmada"));

        service.undo("job-b_1|job-c_1");

        assertThat(service.group("job-a_1").detections()).extracting(d -> d.faceId())
                .containsExactly("job-b_1");
        assertThat(service.group("job-c_1").detections()).isEmpty();
    }

    @Test
    void undoOfUnknownDecisionIs404() {
        ResponseStatusException e = failure(() -> service.undo("nada"));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(e.getReason()).isEqualTo("Decisión no encontrada");
    }

    @Test
    void matchCountsCountGroupMembers() {
        service.decide(request("job-a_1", "job-b_1", "confirmada"));
        service.decide(request("job-b_1", "job-c_1", "confirmada"));
        service.decide(request("job-c_1", "job-d_1", "descartada"));

        Map<String, Integer> counts = service.matchCounts(List.of("job-a_1", "job-b_1", "job-c_1", "job-d_1"));

        assertThat(counts).containsEntry("job-a_1", 2)
                .containsEntry("job-b_1", 2)
                .containsEntry("job-c_1", 2)
                .containsEntry("job-d_1", 0);
    }

    @Test
    void withMatchCountsFillsTheAppearances() {
        service.decide(request("job-a_1", "job-b_1", "confirmada"));

        List<FaceAppearance> result = service.withMatchCounts(List.of(
                appearances.get("job-a_1"), appearances.get("job-c_1")));

        assertThat(result.get(0).getMatchCount()).isEqualTo(1);
        assertThat(result.get(1).getMatchCount()).isEqualTo(0);
    }

    @Test
    void hasConfirmedMatchAndDecidedWith() {
        service.decide(request("job-a_1", "job-b_1", "confirmada"));
        service.decide(request("job-a_1", "job-c_1", "descartada"));

        assertThat(service.hasConfirmedMatch(List.of("job-b_1"))).isTrue();
        assertThat(service.hasConfirmedMatch(List.of("job-c_1", "job-d_1"))).isFalse();
        assertThat(service.decidedWith("job-a_1")).containsExactlyInAnyOrder("job-b_1", "job-c_1");
    }
}
