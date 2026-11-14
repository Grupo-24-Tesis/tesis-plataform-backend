package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.api.ApiExceptionHandler;
import com.facecluster.facecluster_backend.domain.MarkedPerson;
import com.facecluster.facecluster_backend.repository.GuardRepository;
import com.facecluster.facecluster_backend.repository.MarkedRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarkedControllerTest {

    private MarkedRepository repository;
    private GuardRepository guardRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository = mock(MarkedRepository.class);
        guardRepository = mock(GuardRepository.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MarkedController(repository, guardRepository))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void marcarUnaDeteccionYaMarcadaResponde409() throws Exception {
        when(repository.existsByFaceId("job-1_4")).thenReturn(true);

        mockMvc.perform(post("/api/marked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"faceId\":\"job-1_4\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("La detección ya está marcada"));

        verify(repository, never()).save(any());
    }

    @Test
    void indiceUnicoDuplicadoAlGuardarResponde409() throws Exception {
        when(repository.existsByFaceId("job-1_4")).thenReturn(false);
        when(repository.save(any(MarkedPerson.class)))
                .thenThrow(new DuplicateKeyException("duplicado"));

        mockMvc.perform(post("/api/marked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"faceId\":\"job-1_4\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void vigilanteDesconocidoResponde400() throws Exception {
        when(guardRepository.existsById("g-x")).thenReturn(false);

        mockMvc.perform(post("/api/marked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"faceId\":\"job-1_4\",\"createdByGuardId\":\"g-x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El vigilante no existe"));

        verify(repository, never()).save(any());
    }

    @Test
    void vigilanteConocidoSeGuardaConLaMarca() throws Exception {
        when(guardRepository.existsById("g-1")).thenReturn(true);
        when(repository.save(any(MarkedPerson.class))).thenAnswer(call -> call.getArgument(0));

        mockMvc.perform(post("/api/marked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"faceId\":\"job-1_4\",\"createdByGuardId\":\"g-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdByGuardId").value("g-1"));
    }

    @Test
    void faceIdEnBlancoResponde400() throws Exception {
        mockMvc.perform(post("/api/marked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"faceId\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("faceId es obligatorio"));

        verify(repository, never()).save(any());
    }

    @Test
    void faceIdAusenteResponde400() throws Exception {
        mockMvc.perform(post("/api/marked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    @Test
    void elIdEnviadoPorElClienteNoSeUsaAlGuardar() throws Exception {
        when(repository.existsByFaceId("job-1_4")).thenReturn(false);
        when(repository.save(any(MarkedPerson.class))).thenAnswer(call -> call.getArgument(0));

        mockMvc.perform(post("/api/marked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"id-del-cliente\",\"faceId\":\"job-1_4\",\"reason\":\"motivo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faceId").value("job-1_4"));

        ArgumentCaptor<MarkedPerson> saved = ArgumentCaptor.forClass(MarkedPerson.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().getFaceId()).isEqualTo("job-1_4");
    }
}
