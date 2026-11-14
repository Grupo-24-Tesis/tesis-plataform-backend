package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.api.ApiExceptionHandler;
import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.MarkedRepository;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import com.facecluster.facecluster_backend.service.FaceImageService;
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
                        faceImageService
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

    private MockMvc byIdsMockMvc(FaceAppearanceRepository repository, FaceImageService faceImageService) {
        return MockMvcBuilders
                .standaloneSetup(new FaceAppearanceController(
                        repository,
                        mock(ReportRepository.class),
                        mock(MarkedRepository.class),
                        faceImageService
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
}
