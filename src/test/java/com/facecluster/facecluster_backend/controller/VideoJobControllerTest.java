package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.api.ApiExceptionHandler;
import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.dto.VideoJobResponse;
import com.facecluster.facecluster_backend.service.FaceMatchService;
import com.facecluster.facecluster_backend.service.JobFacesService;
import com.facecluster.facecluster_backend.service.VideoJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VideoJobControllerTest {

    private VideoJobService videoJobService;
    private JobFacesService jobFacesService;
    private FaceMatchService faceMatchService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        videoJobService = mock(VideoJobService.class);
        jobFacesService = mock(JobFacesService.class);
        faceMatchService = mock(FaceMatchService.class);
        when(faceMatchService.withMatchCounts(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VideoJobController(videoJobService, jobFacesService, faceMatchService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private VideoJobResponse response(String jobId, String cameraId) {
        return new VideoJobResponse(
                jobId,
                "video.mp4",
                "bucket",
                "videos/video.mp4",
                cameraId,
                "COMPLETED",
                Instant.parse("2026-11-14T12:00:00Z"),
                Instant.parse("2026-11-14T12:05:00Z"),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    void listsAllJobsWhenCameraIdIsMissing() throws Exception {
        when(videoJobService.listJobs(null))
                .thenReturn(List.of(response("job-2", "cam-1"), response("job-1", "cam-2")));

        mockMvc.perform(get("/api/video-jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value("job-2"))
                .andExpect(jsonPath("$[1].jobId").value("job-1"));
    }

    @Test
    void listsJobsOfTheGivenCamera() throws Exception {
        when(videoJobService.listJobs("cam-1"))
                .thenReturn(List.of(response("job-2", "cam-1")));

        mockMvc.perform(get("/api/video-jobs").param("cameraId", "cam-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cameraId").value("cam-1"));

        verify(videoJobService).listJobs("cam-1");
    }

    @Test
    void returnsFacesOfAJob() throws Exception {
        FaceAppearance face = new FaceAppearance();
        face.setId("job-1_1");
        face.setJobId("job-1");
        face.setFaceId("job-1_1");
        face.setFrameUrl("https://presigned/url");
        when(jobFacesService.getFaces("job-1")).thenReturn(List.of(face));

        mockMvc.perform(get("/api/video-jobs/job-1/faces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].faceId").value("job-1_1"))
                .andExpect(jsonPath("$[0].frameUrl").value("https://presigned/url"));
    }

    @Test
    void returnsNotFoundWhenTheJobDoesNotExist() throws Exception {
        when(jobFacesService.getFaces("missing"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Trabajo no encontrado"));

        mockMvc.perform(get("/api/video-jobs/missing/faces"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Trabajo no encontrado"));
    }

    @Test
    void retryReturnsTheUpdatedJob() throws Exception {
        when(videoJobService.retryJob("job-1")).thenReturn(response("job-1", "CAM-01"));

        mockMvc.perform(post("/api/video-jobs/job-1/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-1"));
    }

    @Test
    void retryOfANonFailedJobIsConflict() throws Exception {
        when(videoJobService.retryJob("job-1"))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT, "Solo se pueden reintentar videos fallidos"));

        mockMvc.perform(post("/api/video-jobs/job-1/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Solo se pueden reintentar videos fallidos"));
    }

    @Test
    void retryOfAMissingJobIsNotFound() throws Exception {
        when(videoJobService.retryJob("missing"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Trabajo no encontrado"));

        mockMvc.perform(post("/api/video-jobs/missing/retry"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Trabajo no encontrado"));
    }
}
