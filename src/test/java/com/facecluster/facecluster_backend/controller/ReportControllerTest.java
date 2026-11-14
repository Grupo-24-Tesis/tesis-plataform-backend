package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.api.ApiExceptionHandler;
import com.facecluster.facecluster_backend.domain.ReportItem;
import com.facecluster.facecluster_backend.repository.GuardRepository;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Range;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-16T15:05:21Z"),
            ZoneId.of("America/Bogota")
    );

    private ReportRepository repository;
    private GuardRepository guardRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository = mock(ReportRepository.class);
        when(repository.save(any(ReportItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        guardRepository = mock(GuardRepository.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReportController(repository, guardRepository, CLOCK))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createWithUnknownGuardRespondsBadRequest() throws Exception {
        when(guardRepository.existsById("g-x")).thenReturn(false);

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"investigacion\",\"createdByGuardId\":\"g-x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El vigilante no existe"));

        verify(repository, never()).save(any());
    }

    @Test
    void createWithKnownGuardStoresIt() throws Exception {
        when(guardRepository.existsById("g-1")).thenReturn(true);

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"investigacion\",\"createdByGuardId\":\"g-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdByGuardId").value("g-1"));
    }

    @Test
    void createWithoutGuardStillWorks() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"investigacion\"}"))
                .andExpect(status().isOk());

        verify(guardRepository, never()).existsById(any());
    }

    @Test
    void getByIdReturnsTheReport() throws Exception {
        ReportItem report = new ReportItem();
        report.setId("r1");
        report.setTitle("Robo");
        when(repository.findById("r1")).thenReturn(Optional.of(report));

        mockMvc.perform(get("/api/reports/r1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Robo"));
    }

    @Test
    void getByIdUnknownRespondsNotFound() throws Exception {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/reports/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Reporte no encontrado"));
    }

    @Test
    void updateStatusRejectsUnknownStatus() throws Exception {
        mockMvc.perform(put("/api/reports/r1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"cerrado\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Estado de reporte no válido"));

        verify(repository, never()).save(any());
    }

    @Test
    void updateStatusRejectsMissingStatus() throws Exception {
        mockMvc.perform(put("/api/reports/r1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Estado de reporte no válido"));
    }

    @Test
    void revertingToInvestigationClearsResolution() throws Exception {
        ReportItem report = new ReportItem();
        report.setId("r1");
        report.setStatus("solucionado");
        report.setResolvedAt(LocalDateTime.parse("2026-10-16T09:00:00"));
        report.setResolutionDescription("resuelto");
        when(repository.findById("r1")).thenReturn(Optional.of(report));

        mockMvc.perform(put("/api/reports/r1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"investigacion\"}"))
                .andExpect(status().isOk());

        assertThat(report.getStatus()).isEqualTo("investigacion");
        assertThat(report.getResolvedAt()).isNull();
        assertThat(report.getResolutionDescription()).isNull();
    }

    @Test
    void solvingKeepsResolutionData() throws Exception {
        ReportItem report = new ReportItem();
        report.setId("r1");
        report.setStatus("investigacion");
        when(repository.findById("r1")).thenReturn(Optional.of(report));

        mockMvc.perform(put("/api/reports/r1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"solucionado\",\"resolvedAt\":\"2026-10-16T09:00:00\",\"resolutionDescription\":\"resuelto\"}"))
                .andExpect(status().isOk());

        assertThat(report.getResolvedAt()).hasToString("2026-10-16T09:00");
        assertThat(report.getResolutionDescription()).isEqualTo("resuelto");
    }

    @Test
    void createSolvedWithoutResolvedAtResponds400() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"status\":\"solucionado\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Un reporte solucionado necesita fecha de solución"));

        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsUnknownStatus() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"status\":\"otro\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Estado de reporte no válido"));
    }

    @Test
    void createSetsCreatedAtFromClockAndIgnoresClientId() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"existente\",\"title\":\"t\",\"status\":\"investigacion\",\"resolutionDescription\":\"x\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ReportItem> saved = ArgumentCaptor.forClass(ReportItem.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().getCreatedAt()).hasToString("2026-10-16T10:05:21");
        assertThat(saved.getValue().getResolutionDescription()).isNull();
    }

    @Test
    void updateStatusToSolvedWithoutResolvedAtResponds400() throws Exception {
        mockMvc.perform(put("/api/reports/r1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"solucionado\",\"resolutionDescription\":\"resuelto\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Un reporte solucionado necesita fecha de solución"));

        verify(repository, never()).save(any());
    }

    @Test
    void filterByDayUsesAnInclusiveRangeOnIncidentDate() throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Range<LocalDateTime>> range = ArgumentCaptor.forClass(Range.class);
        when(repository.findByIncidentDateBetween(range.capture())).thenReturn(List.of());

        mockMvc.perform(get("/api/reports/filter").param("date", "2026-08-21"))
                .andExpect(status().isOk());

        assertInclusiveDay(range.getValue());
    }

    @Test
    void filterByStatusAndResolutionDayUsesAnInclusiveRangeOnResolvedAt() throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Range<LocalDateTime>> range = ArgumentCaptor.forClass(Range.class);
        when(repository.findByStatusAndResolvedAtBetween(eq("solucionado"), range.capture()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/reports/filter")
                        .param("status", "solucionado")
                        .param("startDate", "2026-08-21")
                        .param("endDate", "2026-08-21")
                        .param("dateType", "resolucion"))
                .andExpect(status().isOk());

        assertInclusiveDay(range.getValue());
    }

    private void assertInclusiveDay(Range<LocalDateTime> range) {
        assertThat(range.getLowerBound().isInclusive()).isTrue();
        assertThat(range.getUpperBound().isInclusive()).isTrue();
        assertThat(range.getLowerBound().getValue()).contains(LocalDateTime.parse("2026-08-21T00:00:00"));
        assertThat(range.getUpperBound().getValue()).contains(LocalDateTime.parse("2026-08-21T23:59:59.999999999"));
    }
}
