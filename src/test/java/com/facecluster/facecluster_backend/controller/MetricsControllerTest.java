package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.api.ApiExceptionHandler;
import com.facecluster.facecluster_backend.dto.VideoJobMetrics;
import com.facecluster.facecluster_backend.service.VideoJobMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetricsControllerTest {

    private VideoJobMetricsService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(VideoJobMetricsService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MetricsController(service))
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
}
