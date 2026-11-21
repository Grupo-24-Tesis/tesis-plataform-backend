package com.facecluster.facecluster_backend.api;

import com.facecluster.facecluster_backend.controller.FaceAppearanceController;
import com.facecluster.facecluster_backend.controller.ReportController;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.MarkedRepository;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import com.facecluster.facecluster_backend.service.FaceImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {

    private FaceAppearanceRepository faceAppearanceRepository;
    private ReportRepository reportRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        faceAppearanceRepository = mock(FaceAppearanceRepository.class);
        reportRepository = mock(ReportRepository.class);
        MarkedRepository markedRepository = mock(MarkedRepository.class);
        FaceImageService faceImageService = mock(FaceImageService.class);

        FaceAppearanceController faceAppearanceController = new FaceAppearanceController(
                faceAppearanceRepository,
                reportRepository,
                markedRepository,
                faceImageService,
                mock(com.facecluster.facecluster_backend.service.FaceMatchService.class)
        );
        ReportController reportController = new ReportController(reportRepository, mock(com.facecluster.facecluster_backend.repository.GuardRepository.class), java.time.Clock.systemUTC());

        mockMvc = MockMvcBuilders
                .standaloneSetup(faceAppearanceController, reportController)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void fechaMalFormadaEnRostrosUnicosResponde400() throws Exception {
        mockMvc.perform(get("/api/face-appearances/unique").param("startDate", "14/11/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Fecha inválida")));

        verify(faceAppearanceRepository, never()).findAll();
    }

    @Test
    void soloFechaFinalEnReportesUsaLaConsultaSinLimiteInferior() throws Exception {
        when(reportRepository.findByIncidentDateLessThanEqual(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/reports/filter").param("endDate", "2026-11-14"))
                .andExpect(status().isOk());

        verify(reportRepository).findByIncidentDateLessThanEqual(
                LocalDateTime.of(2026, 11, 14, 0, 0).with(LocalTime.MAX)
        );
    }

    @Test
    void reporteInexistenteResponde404() throws Exception {
        when(reportRepository.findById("x")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/reports/x/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"solucionado\",\"resolvedAt\":\"2026-11-14T09:00:00\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Reporte no encontrado"));
    }

    @Test
    void cuerpoMalFormadoResponde400() throws Exception {
        mockMvc.perform(put("/api/reports/x/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo de la petición no es válido"));
    }
}
