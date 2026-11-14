package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.api.ApiExceptionHandler;
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
}
