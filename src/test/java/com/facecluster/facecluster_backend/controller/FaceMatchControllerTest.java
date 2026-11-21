package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.api.ApiExceptionHandler;
import com.facecluster.facecluster_backend.domain.FaceMatch;
import com.facecluster.facecluster_backend.dto.FaceMatchRequest;
import com.facecluster.facecluster_backend.service.FaceMatchService;
import com.facecluster.facecluster_backend.service.FaceMatchService.DecisionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FaceMatchControllerTest {

    private static final String BODY =
            "{\"faceIdA\":\"job-a_1\",\"faceIdB\":\"job-b_1\",\"decision\":\"confirmada\",\"score\":0.8}";

    private FaceMatchService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(FaceMatchService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FaceMatchController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private FaceMatch match() {
        FaceMatch match = new FaceMatch();
        match.setId("job-a_1|job-b_1");
        match.setFaceIdA("job-a_1");
        match.setFaceIdB("job-b_1");
        match.setDecision("confirmada");
        return match;
    }

    @Test
    void postCreatesWith201() throws Exception {
        when(service.decide(any(FaceMatchRequest.class))).thenReturn(new DecisionResult(match(), true));

        mockMvc.perform(post("/api/face-matches").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("job-a_1|job-b_1"));
    }

    @Test
    void postRepeatedAnswers200() throws Exception {
        when(service.decide(any(FaceMatchRequest.class))).thenReturn(new DecisionResult(match(), false));

        mockMvc.perform(post("/api/face-matches").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void postInvalidAnswersJson400InSpanish() throws Exception {
        when(service.decide(any(FaceMatchRequest.class))).thenThrow(new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Decisión inválida: use confirmada o descartada"));

        mockMvc.perform(post("/api/face-matches").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Decisión inválida: use confirmada o descartada"));
    }

    @Test
    void deleteAnswers204() throws Exception {
        mockMvc.perform(delete("/api/face-matches/job-a_1|job-b_1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUnknownAnswers404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Decisión no encontrada"))
                .when(service).undo("nada");

        mockMvc.perform(delete("/api/face-matches/nada"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Decisión no encontrada"));
    }
}
