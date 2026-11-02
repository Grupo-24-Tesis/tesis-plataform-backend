package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.controller.ReportController.StatusRequest;
import com.facecluster.facecluster_backend.domain.ReportItem;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** PR-01 – Pruebas unitarias de ReportController (repositorio simulado). */
@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);
    private static final LocalDateTime DAY_START = DAY.atStartOfDay();
    private static final LocalDateTime DAY_END = DAY.atTime(LocalTime.MAX);

    private static final LocalDate END_DAY = LocalDate.of(2026, 10, 10);
    private static final LocalDateTime END_DAY_END = END_DAY.atTime(LocalTime.MAX);

    @Mock
    private ReportRepository repo;

    @InjectMocks
    private ReportController controller;

    private final List<ReportItem> expected = List.of(new ReportItem());

    // ---------- all / create ----------

    @Test
    @DisplayName("all() devuelve todos los reportes")
    void allReturnsEveryReport() {
        when(repo.findAll()).thenReturn(expected);

        assertSame(expected, controller.all());
    }

    @Test
    @DisplayName("create() asigna createdAt automáticamente y conserva incidentDate")
    void createSetsCreatedAt() {
        when(repo.save(any(ReportItem.class))).thenAnswer(i -> i.getArgument(0));
        ReportItem body = new ReportItem();
        LocalDateTime incident = LocalDateTime.of(2026, 10, 1, 8, 0);
        body.setIncidentDate(incident);
        LocalDateTime before = LocalDateTime.now();

        ReportItem saved = controller.create(body);

        LocalDateTime after = LocalDateTime.now();
        assertNotNull(saved.getCreatedAt());
        assertFalse(saved.getCreatedAt().isBefore(before));
        assertFalse(saved.getCreatedAt().isAfter(after));
        assertEquals(incident, saved.getIncidentDate());
    }

    @Test
    @DisplayName("create() sobrescribe un createdAt enviado por el cliente")
    void createOverridesClientCreatedAt() {
        when(repo.save(any(ReportItem.class))).thenAnswer(i -> i.getArgument(0));
        ReportItem body = new ReportItem();
        LocalDateTime forged = LocalDateTime.of(2000, 1, 1, 0, 0);
        body.setCreatedAt(forged);

        ReportItem saved = controller.create(body);

        assertTrue(saved.getCreatedAt().isAfter(forged));
    }

    // ---------- filter ----------

    @Test
    @DisplayName("filter sin parámetros devuelve todos")
    void filterWithoutParamsReturnsAll() {
        when(repo.findAll()).thenReturn(expected);

        assertSame(expected, controller.filter(null, null, null, null, null));
        verify(repo).findAll();
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("filter solo por estado")
    void filterByStatusOnly() {
        when(repo.findByStatus("abierto")).thenReturn(expected);

        assertSame(expected, controller.filter("abierto", null, null, null, null));
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("filter por día (fecha del incidente por defecto)")
    void filterByDayUsesIncidentDate() {
        when(repo.findByIncidentDateBetween(DAY_START, DAY_END)).thenReturn(expected);

        assertSame(expected, controller.filter(null, "2026-10-05", null, null, null));
        verifyNoMoreInteractions(repo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"resolucion", "RESOLUCION", "Resolucion"})
    @DisplayName("filter por día con dateType=resolucion usa resolvedAt (sin distinguir mayúsculas)")
    void filterByDayUsesResolutionDate(String dateType) {
        when(repo.findByResolvedAtBetween(DAY_START, DAY_END)).thenReturn(expected);

        assertSame(expected, controller.filter(null, "2026-10-05", null, null, dateType));
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("dateType desconocido se trata como fecha del incidente")
    void unknownDateTypeFallsBackToIncidentDate() {
        when(repo.findByIncidentDateBetween(DAY_START, DAY_END)).thenReturn(expected);

        assertSame(expected, controller.filter(null, "2026-10-05", null, null, "otro"));
    }

    @Test
    @DisplayName("filter por rango de fechas del incidente")
    void filterByDateRange() {
        when(repo.findByIncidentDateBetween(DAY_START, END_DAY_END)).thenReturn(expected);

        assertSame(expected, controller.filter(null, null, "2026-10-05", "2026-10-10", null));
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("filter por rango de fechas de resolución")
    void filterByResolutionRange() {
        when(repo.findByResolvedAtBetween(DAY_START, END_DAY_END)).thenReturn(expected);

        assertSame(expected, controller.filter(null, null, "2026-10-05", "2026-10-10", "resolucion"));
    }

    @Test
    @DisplayName("Si llegan 'date' y rango, gana 'date'")
    void singleDateTakesPrecedenceOverRange() {
        when(repo.findByIncidentDateBetween(DAY_START, DAY_END)).thenReturn(expected);

        assertSame(expected, controller.filter(null, "2026-10-05", "2026-01-01", "2026-12-31", null));
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("filter por estado + día (incidente)")
    void filterByStatusAndIncidentDay() {
        when(repo.findByStatusAndIncidentDateBetween("abierto", DAY_START, DAY_END)).thenReturn(expected);

        assertSame(expected, controller.filter("abierto", "2026-10-05", null, null, null));
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("filter por estado + día (resolución)")
    void filterByStatusAndResolutionDay() {
        when(repo.findByStatusAndResolvedAtBetween("solucionado", DAY_START, DAY_END)).thenReturn(expected);

        assertSame(expected, controller.filter("solucionado", "2026-10-05", null, null, "resolucion"));
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("filter por estado + rango")
    void filterByStatusAndRange() {
        when(repo.findByStatusAndIncidentDateBetween("abierto", DAY_START, END_DAY_END)).thenReturn(expected);

        assertSame(expected, controller.filter("abierto", null, "2026-10-05", "2026-10-10", null));
    }

    @Test
    @DisplayName("Rango incompleto (solo startDate) y sin estado: devuelve todos")
    void incompleteRangeFallsBackToFindAll() {
        when(repo.findAll()).thenReturn(expected);

        assertSame(expected, controller.filter(null, null, "2026-10-05", null, null));
        verify(repo).findAll();
        verifyNoMoreInteractions(repo);
    }

    @Test
    @DisplayName("Estado en blanco y sin fechas: devuelve todos")
    void blankStatusFallsBackToFindAll() {
        when(repo.findAll()).thenReturn(expected);

        assertSame(expected, controller.filter("  ", null, null, null, null));
    }

    @Test
    @DisplayName("Fecha con formato inválido lanza DateTimeParseException")
    void invalidDateThrows() {
        assertThrows(DateTimeParseException.class,
                () -> controller.filter(null, "05-10-2026", null, null, null));
    }

    // ---------- updateStatus ----------

    @Test
    @DisplayName("Reporte inexistente -> RuntimeException 'Reporte no encontrado'")
    void updateStatusUnknownReportThrows() {
        when(repo.findById("x")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> controller.updateStatus("x", new StatusRequest("abierto", null, null)));

        assertEquals("Reporte no encontrado", ex.getMessage());
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("Al marcar 'solucionado' guarda resolvedAt y la descripción de la resolución")
    void updateStatusToResolvedStoresResolution() {
        ReportItem report = new ReportItem();
        when(repo.findById("r1")).thenReturn(Optional.of(report));
        when(repo.save(any(ReportItem.class))).thenAnswer(i -> i.getArgument(0));
        LocalDateTime resolvedAt = LocalDateTime.of(2026, 10, 6, 12, 0);

        ReportItem result = controller.updateStatus("r1",
                new StatusRequest("solucionado", resolvedAt, "Se contactó a la persona"));

        assertEquals("solucionado", result.getStatus());
        assertEquals(resolvedAt, result.getResolvedAt());
        assertEquals("Se contactó a la persona", result.getResolutionDescription());
    }

    @Test
    @DisplayName("Con otro estado solo cambia el estado y no toca los campos de resolución")
    void updateStatusToOtherDoesNotTouchResolution() {
        ReportItem report = new ReportItem();
        when(repo.findById("r1")).thenReturn(Optional.of(report));
        when(repo.save(any(ReportItem.class))).thenAnswer(i -> i.getArgument(0));

        ReportItem result = controller.updateStatus("r1",
                new StatusRequest("en_proceso", LocalDateTime.now(), "no debería guardarse"));

        assertEquals("en_proceso", result.getStatus());
        assertNull(result.getResolvedAt());
        assertNull(result.getResolutionDescription());
    }
}
