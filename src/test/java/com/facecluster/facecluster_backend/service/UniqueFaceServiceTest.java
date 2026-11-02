package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.domain.MarkedPerson;
import com.facecluster.facecluster_backend.domain.ReportItem;
import com.facecluster.facecluster_backend.dto.UniqueFace;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.MarkedRepository;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * PR-01 – Pruebas unitarias de UniqueFaceService.
 * Los repositorios se simulan: no se usa MongoDB real.
 */
@ExtendWith(MockitoExtension.class)
class UniqueFaceServiceTest {

    @Mock
    private FaceAppearanceRepository faceAppearanceRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private MarkedRepository markedRepository;

    @InjectMocks
    private UniqueFaceService service;

    // ---------- helpers ----------

    private FaceAppearance appearance(String faceId, String url, LocalDateTime capturedAt) {
        FaceAppearance a = new FaceAppearance();
        a.setFaceId(faceId);
        a.setFrameUrl(url);
        a.setCapturedAt(capturedAt);
        return a;
    }

    private ReportItem report(String... faceIds) {
        ReportItem r = new ReportItem();
        r.setFaceIds(faceIds == null ? null : new ArrayList<>(Arrays.asList(faceIds)));
        return r;
    }

    private MarkedPerson marked(String faceId) {
        MarkedPerson m = new MarkedPerson();
        m.setFaceId(faceId);
        return m;
    }

    private void givenData(List<FaceAppearance> appearances,
                           List<ReportItem> reports,
                           List<MarkedPerson> marked) {
        when(faceAppearanceRepository.findAll()).thenReturn(appearances);
        when(reportRepository.findAll()).thenReturn(reports);
        when(markedRepository.findAll()).thenReturn(marked);
    }

    private Map<String, UniqueFace> byFaceId(List<UniqueFace> faces) {
        return faces.stream().collect(Collectors.toMap(UniqueFace::getFaceId, Function.identity()));
    }

    // ---------- pruebas ----------

    @Test
    @DisplayName("Sin datos devuelve lista vacía")
    void returnsEmptyListWhenThereIsNoData() {
        givenData(List.of(), List.of(), List.of());

        assertTrue(service.getUniqueFaces().isEmpty());
    }

    @Test
    @DisplayName("Una aparición sin reportes ni marcas: reportCount=0 y suspect=false")
    void singleAppearanceWithoutReportsOrMarks() {
        LocalDateTime capturedAt = LocalDateTime.of(2026, 10, 5, 9, 0);
        givenData(List.of(appearance("f1", "http://img/1.jpg", capturedAt)), List.of(), List.of());

        List<UniqueFace> result = service.getUniqueFaces();

        assertEquals(1, result.size());
        UniqueFace face = result.get(0);
        assertEquals("f1", face.getFaceId());
        assertEquals("http://img/1.jpg", face.getImageUrl());
        assertEquals(capturedAt, face.getCapturedAt());
        assertEquals(0, face.getReportCount());
        assertFalse(face.isSuspect());
    }

    @Test
    @DisplayName("Varias apariciones de la misma cara producen una sola cara única (la primera es la representativa)")
    void groupsAppearancesByFaceIdKeepingFirst() {
        givenData(List.of(
                        appearance("f1", "http://img/primera.jpg", LocalDateTime.of(2026, 10, 5, 9, 0)),
                        appearance("f1", "http://img/segunda.jpg", LocalDateTime.of(2026, 10, 5, 10, 0))),
                List.of(), List.of());

        List<UniqueFace> result = service.getUniqueFaces();

        assertEquals(1, result.size());
        assertEquals("http://img/primera.jpg", result.get(0).getImageUrl());
    }

    @Test
    @DisplayName("Cuenta en cuántos reportes aparece cada cara")
    void countsReportsPerFace() {
        givenData(
                List.of(appearance("f1", "u1", null), appearance("f2", "u2", null)),
                List.of(report("f1", "f2"), report("f1"), report("f3")),
                List.of());

        Map<String, UniqueFace> result = byFaceId(service.getUniqueFaces());

        assertEquals(2, result.get("f1").getReportCount());
        assertEquals(1, result.get("f2").getReportCount());
    }

    @Test
    @DisplayName("Una cara repetida dentro del mismo reporte cuenta una sola vez")
    void duplicateFaceInsideSameReportCountsOnce() {
        givenData(
                List.of(appearance("f1", "u1", null)),
                List.of(report("f1", "f1", "f1")),
                List.of());

        assertEquals(1, service.getUniqueFaces().get(0).getReportCount());
    }

    @Test
    @DisplayName("Ignora reportes sin lista de caras (null)")
    void ignoresReportsWithNullFaceIds() {
        givenData(
                List.of(appearance("f1", "u1", null)),
                List.of(report((String[]) null), report("f1")),
                List.of());

        assertEquals(1, service.getUniqueFaces().get(0).getReportCount());
    }

    @Test
    @DisplayName("Marca como sospechosas solo las caras registradas en 'marked'")
    void flagsOnlyMarkedFacesAsSuspect() {
        givenData(
                List.of(appearance("f1", "u1", null), appearance("f2", "u2", null)),
                List.of(),
                List.of(marked("f2")));

        Map<String, UniqueFace> result = byFaceId(service.getUniqueFaces());

        assertFalse(result.get("f1").isSuspect());
        assertTrue(result.get("f2").isSuspect());
    }

    @Test
    @DisplayName("Una marca o reporte de una cara sin apariciones no genera cara única")
    void facesWithoutAppearancesAreNotReturned() {
        givenData(
                List.of(appearance("f1", "u1", null)),
                List.of(report("fantasma")),
                List.of(marked("fantasma")));

        List<UniqueFace> result = service.getUniqueFaces();

        assertEquals(1, result.size());
        assertEquals("f1", result.get(0).getFaceId());
    }
}
