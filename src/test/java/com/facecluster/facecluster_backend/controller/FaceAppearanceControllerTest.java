package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.controller.FaceAppearanceController.UniqueFaceResponse;
import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.domain.MarkedPerson;
import com.facecluster.facecluster_backend.domain.ReportItem;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.MarkedRepository;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import com.facecluster.facecluster_backend.service.FaceImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import static org.mockito.ArgumentMatchers.anyString;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** PR-01 – Pruebas unitarias de FaceAppearanceController. */
@ExtendWith(MockitoExtension.class)
class FaceAppearanceControllerTest {

    @Mock
    private FaceAppearanceRepository repository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private MarkedRepository markedRepository;
    @Mock
    private FaceImageService faceImageService;

    @InjectMocks
    private FaceAppearanceController controller;

    @BeforeEach
    void defaultStubs() {
        // Por defecto, el servicio de imágenes devuelve las mismas apariciones
        lenient().when(faceImageService.withS3Images(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // Por defecto, ninguna cara tiene reportes ni está marcada;
        // cada prueba sobrescribe esto para las caras que le interesan
        lenient().when(reportRepository.findByFaceIdsContaining(anyString()))
                .thenReturn(List.of());
        lenient().when(markedRepository.findByFaceId(anyString()))
                .thenReturn(Optional.empty());
    }

    private FaceAppearance appearance(String id, String faceId) {
        FaceAppearance a = new FaceAppearance();
        a.setId(id);
        a.setFaceId(faceId);
        return a;
    }

    private Map<String, UniqueFaceResponse> byFaceId(List<UniqueFaceResponse> list) {
        return list.stream().collect(Collectors.toMap(UniqueFaceResponse::faceId, Function.identity()));
    }

    // ---------- findAll / create ----------

    @Test
    @DisplayName("findAll() devuelve lo que filtra FaceImageService")
    void findAllDelegatesToImageService() {
        List<FaceAppearance> stored = List.of(appearance("1", "f1"), appearance("2", "f2"));
        List<FaceAppearance> visible = List.of(appearance("1", "f1"));
        when(repository.findAll()).thenReturn(stored);
        when(faceImageService.withS3Images(stored)).thenReturn(visible);

        assertSame(visible, controller.findAll());
    }

    @Test
    @DisplayName("create() guarda y devuelve la aparición")
    void createSavesAppearance() {
        FaceAppearance body = appearance(null, "f1");
        when(repository.save(body)).thenReturn(body);

        assertSame(body, controller.create(body));
    }

    // ---------- findUniqueFaces ----------

    @Test
    @DisplayName("Sin fechas consulta todas las apariciones")
    void withoutDatesUsesFindAll() {
        when(repository.findAll()).thenReturn(List.of(appearance("1", "f1")));

        List<UniqueFaceResponse> result = controller.findUniqueFaces(null, null, null, null);

        assertEquals(1, result.size());
        verify(repository, never()).findByCapturedAtBetween(any(), any());
    }

    @Test
    @DisplayName("Con startDate y endDate consulta por rango (inicio del día a fin del día)")
    void withDatesUsesRangeQuery() {
        LocalDateTime start = LocalDate.of(2026, 10, 1).atStartOfDay();
        LocalDateTime end = LocalDate.of(2026, 10, 5).atTime(LocalTime.MAX);
        when(repository.findByCapturedAtBetween(start, end)).thenReturn(List.of(appearance("1", "f1")));

        List<UniqueFaceResponse> result =
                controller.findUniqueFaces("2026-10-01", "2026-10-05", null, null);

        assertEquals(1, result.size());
        verify(repository, never()).findAll();
    }

    @Test
    @DisplayName("Con solo una de las dos fechas se ignora el filtro de fecha")
    void singleDateIsIgnored() {
        when(repository.findAll()).thenReturn(List.of());

        controller.findUniqueFaces("2026-10-01", null, null, null);
        controller.findUniqueFaces("  ", "  ", null, null);

        verify(repository, times(2)).findAll();
        verify(repository, never()).findByCapturedAtBetween(any(), any());
    }

    @Test
    @DisplayName("Agrupa las apariciones por faceId")
    void groupsByFaceId() {
        when(repository.findAll()).thenReturn(List.of(
                appearance("1", "f1"), appearance("2", "f2"), appearance("3", "f1")));

        Map<String, UniqueFaceResponse> result = byFaceId(controller.findUniqueFaces(null, null, null, null));

        assertEquals(2, result.size());
        assertEquals(2, result.get("f1").appearances().size());
        assertEquals(1, result.get("f2").appearances().size());
    }

    @Test
    @DisplayName("Calcula reportCount y suspect de cada cara")
    void computesReportCountAndSuspect() {
        when(repository.findAll()).thenReturn(List.of(appearance("1", "f1"), appearance("2", "f2")));
        when(reportRepository.findByFaceIdsContaining("f1"))
                .thenReturn(List.of(new ReportItem(), new ReportItem()));
        when(markedRepository.findByFaceId("f2")).thenReturn(Optional.of(new MarkedPerson()));

        Map<String, UniqueFaceResponse> result = byFaceId(controller.findUniqueFaces(null, null, null, null));

        assertEquals(2, result.get("f1").reportCount());
        assertFalse(result.get("f1").suspect());
        assertEquals(0, result.get("f2").reportCount());
        assertTrue(result.get("f2").suspect());
    }

    @Test
    @DisplayName("Si ninguna imagen existe en S3, no hay caras en la respuesta")
    void noImagesInS3ProducesEmptyResponse() {
        when(repository.findAll()).thenReturn(List.of(appearance("1", "f1")));
        when(faceImageService.withS3Images(anyList())).thenReturn(List.of());

        assertTrue(controller.findUniqueFaces(null, null, null, null).isEmpty());
    }

    private void givenFaceWithReportsAndFaceWithoutAndSuspectOne() {
        // f1: con reportes, no sospechosa | f2: sin reportes, sospechosa | f3: sin reportes ni marca
        when(repository.findAll()).thenReturn(List.of(
                appearance("1", "f1"), appearance("2", "f2"), appearance("3", "f3")));
        when(reportRepository.findByFaceIdsContaining("f1")).thenReturn(List.of(new ReportItem()));
        when(markedRepository.findByFaceId("f2")).thenReturn(Optional.of(new MarkedPerson()));
    }

    @Test
    @DisplayName("Filtro hasReports=true deja solo las caras con reportes")
    void filtersByHasReportsTrue() {
        givenFaceWithReportsAndFaceWithoutAndSuspectOne();

        List<UniqueFaceResponse> result = controller.findUniqueFaces(null, null, true, null);

        assertEquals(List.of("f1"), result.stream().map(UniqueFaceResponse::faceId).toList());
    }

    @Test
    @DisplayName("Filtro hasReports=false deja solo las caras sin reportes")
    void filtersByHasReportsFalse() {
        givenFaceWithReportsAndFaceWithoutAndSuspectOne();

        List<String> ids = controller.findUniqueFaces(null, null, false, null)
                .stream().map(UniqueFaceResponse::faceId).sorted().toList();

        assertEquals(List.of("f2", "f3"), ids);
    }

    @Test
    @DisplayName("Filtro suspect=true deja solo las sospechosas")
    void filtersBySuspectTrue() {
        givenFaceWithReportsAndFaceWithoutAndSuspectOne();

        List<UniqueFaceResponse> result = controller.findUniqueFaces(null, null, null, true);

        assertEquals(List.of("f2"), result.stream().map(UniqueFaceResponse::faceId).toList());
    }

    @Test
    @DisplayName("Filtro suspect=false excluye a las sospechosas")
    void filtersBySuspectFalse() {
        givenFaceWithReportsAndFaceWithoutAndSuspectOne();

        List<String> ids = controller.findUniqueFaces(null, null, null, false)
                .stream().map(UniqueFaceResponse::faceId).sorted().toList();

        assertEquals(List.of("f1", "f3"), ids);
    }

    @Test
    @DisplayName("Filtros combinados: sin reportes y sospechosa")
    void combinesFilters() {
        givenFaceWithReportsAndFaceWithoutAndSuspectOne();

        List<UniqueFaceResponse> result = controller.findUniqueFaces(null, null, false, true);

        assertEquals(List.of("f2"), result.stream().map(UniqueFaceResponse::faceId).toList());
    }
}
