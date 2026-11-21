package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.api.ApiExceptionHandler;
import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.dto.DetectionMatchesResponse;
import com.facecluster.facecluster_backend.dto.DetectionMatchesResponse.MatchLink;
import com.facecluster.facecluster_backend.dto.MatchDetectionResponse;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.MarkedRepository;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import com.facecluster.facecluster_backend.service.FaceImageService;
import com.facecluster.facecluster_backend.service.FaceMatchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Range;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FaceAppearanceControllerTest {

    @Test
    void uniqueWithBothDatesUsesAnInclusiveRangeOnCapturedAt() throws Exception {
        FaceAppearanceRepository repository = mock(FaceAppearanceRepository.class);
        FaceImageService faceImageService = mock(FaceImageService.class);
        when(faceImageService.withS3Images(anyList())).thenReturn(List.of());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Range<LocalDateTime>> range = ArgumentCaptor.forClass(Range.class);
        when(repository.findByCapturedAtBetween(range.capture())).thenReturn(List.of());

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new FaceAppearanceController(
                        repository,
                        mock(ReportRepository.class),
                        mock(MarkedRepository.class),
                        faceImageService,
                        faceMatchService
                ))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(get("/api/face-appearances/unique")
                        .param("startDate", "2026-08-21")
                        .param("endDate", "2026-08-22"))
                .andExpect(status().isOk());

        assertThat(range.getValue().getLowerBound().isInclusive()).isTrue();
        assertThat(range.getValue().getUpperBound().isInclusive()).isTrue();
        assertThat(range.getValue().getLowerBound().getValue())
                .contains(LocalDateTime.parse("2026-08-21T00:00:00"));
        assertThat(range.getValue().getUpperBound().getValue())
                .contains(LocalDateTime.parse("2026-08-22T23:59:59.999999999"));
    }

    private final FaceMatchService faceMatchService = passthroughMatchService();

    private static FaceMatchService passthroughMatchService() {
        FaceMatchService mocked = mock(FaceMatchService.class);
        when(mocked.withMatchCounts(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        return mocked;
    }

    private MockMvc byIdsMockMvc(FaceAppearanceRepository repository, FaceImageService faceImageService) {
        return MockMvcBuilders
                .standaloneSetup(new FaceAppearanceController(
                        repository,
                        mock(ReportRepository.class),
                        mock(MarkedRepository.class),
                        faceImageService,
                        faceMatchService
                ))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void byIdsReturnsOnlyKnownAppearancesWithImages() throws Exception {
        FaceAppearanceRepository repository = mock(FaceAppearanceRepository.class);
        FaceImageService faceImageService = mock(FaceImageService.class);
        FaceAppearance found = new FaceAppearance();
        found.setFaceId("job-1_1");
        found.setJobId("job-1");
        when(repository.findAllById(List.of("job-1_1", "job-1_2"))).thenReturn(List.of(found));
        when(faceImageService.withS3ImagesByJob(List.of(found))).thenReturn(List.of(found));

        byIdsMockMvc(repository, faceImageService)
                .perform(get("/api/face-appearances/by-ids").param("faceIds", "job-1_1,job-1_2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].faceId").value("job-1_1"));
    }

    @Test
    void byIdsRejectsMoreThanFiftyIds() throws Exception {
        String ids = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(i -> "job-1_" + i)
                .collect(java.util.stream.Collectors.joining(","));

        byIdsMockMvc(mock(FaceAppearanceRepository.class), mock(FaceImageService.class))
                .perform(get("/api/face-appearances/by-ids").param("faceIds", ids))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Se permiten como máximo 50 detecciones por consulta"));
    }

    @Test
    void byIdsWithoutFaceIdsAnswersJsonBadRequestInSpanish() throws Exception {
        byIdsMockMvc(mock(FaceAppearanceRepository.class), mock(FaceImageService.class))
                .perform(get("/api/face-appearances/by-ids"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Falta el parámetro obligatorio faceIds"));
    }

    @Test
    void byIdsAcceptsExactlyFiftyIds() throws Exception {
        FaceAppearanceRepository repository = mock(FaceAppearanceRepository.class);
        FaceImageService faceImageService = mock(FaceImageService.class);
        when(repository.findAllById(anyList())).thenReturn(List.of());
        when(faceImageService.withS3ImagesByJob(anyList())).thenReturn(List.of());
        String ids = java.util.stream.IntStream.rangeClosed(1, 50)
                .mapToObj(i -> "job-1_" + i)
                .collect(java.util.stream.Collectors.joining(","));

        byIdsMockMvc(repository, faceImageService)
                .perform(get("/api/face-appearances/by-ids").param("faceIds", ids))
                .andExpect(status().isOk());
    }

    @Test
    void matchesEndpointReturnsGroup() throws Exception {
        MatchDetectionResponse other = new MatchDetectionResponse(
                "job-2_1", "job-2", "cam-2", "Torre", "b.mp4", "00:00:05",
                LocalDateTime.parse("2026-11-20T10:00:00"), null, "https://presigned/b"
        );
        MatchLink link = new MatchLink(
                "job-1_1|job-2_1", "job-1_1", "job-2_1", null,
                LocalDateTime.parse("2026-11-20T11:00:00")
        );
        when(faceMatchService.group("job-1_1"))
                .thenReturn(new DetectionMatchesResponse("job-1_1", List.of(link), List.of(other)));

        byIdsMockMvc(mock(FaceAppearanceRepository.class), mock(FaceImageService.class))
                .perform(get("/api/face-appearances/job-1_1/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faceId").value("job-1_1"))
                .andExpect(jsonPath("$.links[0].id").value("job-1_1|job-2_1"))
                .andExpect(jsonPath("$.detections[0].faceId").value("job-2_1"))
                .andExpect(jsonPath("$.detections[0].cameraName").value("Torre"));
    }

    @Test
    void listsCarryMatchCount() throws Exception {
        FaceAppearanceRepository repository = mock(FaceAppearanceRepository.class);
        FaceImageService faceImageService = mock(FaceImageService.class);
        FaceAppearance found = new FaceAppearance();
        found.setFaceId("job-1_1");
        found.setJobId("job-1");
        when(repository.findAll()).thenReturn(List.of(found));
        when(repository.findAllById(List.of("job-1_1"))).thenReturn(List.of(found));
        when(faceImageService.withS3Images(List.of(found))).thenReturn(List.of(found));
        when(faceImageService.withS3ImagesByJob(List.of(found))).thenReturn(List.of(found));
        when(faceMatchService.withMatchCounts(List.of(found))).thenAnswer(invocation -> {
            found.setMatchCount(2);
            return List.of(found);
        });

        MockMvc mockMvc = byIdsMockMvc(repository, faceImageService);

        mockMvc.perform(get("/api/face-appearances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matchCount").value(2));
        mockMvc.perform(get("/api/face-appearances/by-ids").param("faceIds", "job-1_1"))
                .andExpect(jsonPath("$[0].matchCount").value(2));
        mockMvc.perform(get("/api/face-appearances/unique"))
                .andExpect(jsonPath("$[0].appearances[0].matchCount").value(2));
    }
}
