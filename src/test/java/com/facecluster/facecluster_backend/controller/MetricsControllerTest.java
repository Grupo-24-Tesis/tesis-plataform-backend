package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.api.ApiExceptionHandler;
import com.facecluster.facecluster_backend.api.DateRangeParams.DateRange;
import com.facecluster.facecluster_backend.dto.ActivityResponse;
import com.facecluster.facecluster_backend.dto.VideoJobMetrics;
import com.facecluster.facecluster_backend.service.ActivityService;
import com.facecluster.facecluster_backend.service.VideoJobMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetricsControllerTest {

    private VideoJobMetricsService service;
    private ActivityService activityService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(VideoJobMetricsService.class);
        activityService = mock(ActivityService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MetricsController(service, activityService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private VideoJobMetrics metrics(long total) {
        return new VideoJobMetrics(total, 2, 1, 1, 0.33, 12.0, 80.0, 6, 3.0);
    }

    @Test
    void returnsMetricsOfAllCamerasWithoutCameraId() throws Exception {
        when(service.calculate(null)).thenReturn(metrics(4));

        mockMvc.perform(get("/api/metrics/video-jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.averageProcessingSeconds").value(80.0))
                .andExpect(jsonPath("$.totalFaces").value(6));
    }

    @Test
    void passesTheCameraIdToTheService() throws Exception {
        when(service.calculate("cam-1")).thenReturn(metrics(2));

        mockMvc.perform(get("/api/metrics/video-jobs").param("cameraId", "cam-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        verify(service).calculate("cam-1");
    }

    @Test
    void returnsActivityWithParsedDateRange() throws Exception {
        DateRange range = new DateRange(
                LocalDate.of(2026, 5, 1).atStartOfDay(),
                LocalDate.of(2026, 5, 31).atTime(LocalTime.MAX)
        );
        ActivityResponse response = new ActivityResponse(
                List.of(new ActivityResponse.HourCount(18, 5)),
                List.of(new ActivityResponse.CameraCount("cam-1", "Entrada", 5)),
                5
        );
        when(activityService.calculate("cam-1", range)).thenReturn(response);

        mockMvc.perform(get("/api/metrics/activity")
                        .param("cameraId", "cam-1")
                        .param("startDate", "2026-05-01")
                        .param("endDate", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.hours[0].hour").value(18))
                .andExpect(jsonPath("$.cameras[0].cameraName").value("Entrada"));
    }

    @Test
    void rejectsMalformedActivityDateWithBadRequest() throws Exception {
        mockMvc.perform(get("/api/metrics/activity").param("startDate", "31-05-2026"))
                .andExpect(status().isBadRequest());

        verify(activityService, never()).calculate(any(), any());
    }
}
