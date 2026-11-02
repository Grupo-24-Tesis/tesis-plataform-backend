package com.facecluster.facecluster_backend.domain;

import com.facecluster.facecluster_backend.dto.UniqueFace;
import com.facecluster.facecluster_backend.dto.VideoResultRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** PR-01 – Pruebas unitarias de entidades y DTOs (getters/setters y constructores). */
class DomainModelsTest {

    private static final LocalDateTime DATE = LocalDateTime.of(2026, 10, 5, 14, 30);

    @Test
    void cameraStoresValues() {
        Camera c = new Camera();
        c.setId("1"); c.setName("Entrada"); c.setActive(true);
        assertEquals("1", c.getId());
        assertEquals("Entrada", c.getName());
        assertTrue(c.getActive());
    }

    @Test
    void guardStoresValues() {
        Guard g = new Guard();
        g.setId("1"); g.setName("Ana"); g.setShift("noche"); g.setSchedule("22:00-06:00");
        assertEquals("1", g.getId());
        assertEquals("Ana", g.getName());
        assertEquals("noche", g.getShift());
        assertEquals("22:00-06:00", g.getSchedule());
    }

    @Test
    void markedPersonStoresValues() {
        MarkedPerson m = new MarkedPerson();
        m.setId("1"); m.setFaceId("f1"); m.setReason("Comportamiento sospechoso");
        assertEquals("1", m.getId());
        assertEquals("f1", m.getFaceId());
        assertEquals("Comportamiento sospechoso", m.getReason());
    }

    @Test
    void faceStoresValues() {
        Face f = new Face();
        assertFalse(f.isSuspicious());
        f.setId("1"); f.setFaceId("f1"); f.setSuspicious(true);
        assertEquals("1", f.getId());
        assertEquals("f1", f.getFaceId());
        assertTrue(f.isSuspicious());
    }

    @Test
    void faceAppearanceStoresValues() {
        FaceAppearance a = new FaceAppearance();
        a.setId("1"); a.setFaceId("f1"); a.setTimestamp("00:00:04");
        a.setCameraId("cam"); a.setFrameUrl("url"); a.setCapturedAt(DATE);
        assertEquals("1", a.getId());
        assertEquals("f1", a.getFaceId());
        assertEquals("00:00:04", a.getTimestamp());
        assertEquals("cam", a.getCameraId());
        assertEquals("url", a.getFrameUrl());
        assertEquals(DATE, a.getCapturedAt());
    }

    @Test
    void reportItemStoresValues() {
        ReportItem r = new ReportItem();
        r.setId("1"); r.setTitle("Robo"); r.setStatus("abierto"); r.setPriority("alta");
        r.setDescription("desc"); r.setIncidentDate(DATE); r.setCreatedAt(DATE.plusHours(1));
        r.setResolvedAt(DATE.plusDays(1)); r.setResolutionDescription("resuelto");
        r.setFaceIds(List.of("f1", "f2"));
        assertEquals("1", r.getId());
        assertEquals("Robo", r.getTitle());
        assertEquals("abierto", r.getStatus());
        assertEquals("alta", r.getPriority());
        assertEquals("desc", r.getDescription());
        assertEquals(DATE, r.getIncidentDate());
        assertEquals(DATE.plusHours(1), r.getCreatedAt());
        assertEquals(DATE.plusDays(1), r.getResolvedAt());
        assertEquals("resuelto", r.getResolutionDescription());
        assertEquals(List.of("f1", "f2"), r.getFaceIds());
    }

    @Test
    void videoJobStoresValues() {
        Instant now = Instant.now();
        VideoJob j = new VideoJob();
        j.setId("1"); j.setOriginalFileName("a.mp4"); j.setBucket("b"); j.setObjectKey("k");
        j.setCameraId("c"); j.setStatus("UPLOADED"); j.setCreatedAt(now); j.setUpdatedAt(now);
        assertEquals("1", j.getId());
        assertEquals("a.mp4", j.getOriginalFileName());
        assertEquals("b", j.getBucket());
        assertEquals("k", j.getObjectKey());
        assertEquals("c", j.getCameraId());
        assertEquals("UPLOADED", j.getStatus());
        assertEquals(now, j.getCreatedAt());
        assertEquals(now, j.getUpdatedAt());
    }

    @Test
    void updateReportStatusRequestStoresStatus() {
        UpdateReportStatusRequest r = new UpdateReportStatusRequest();
        r.setStatus("solucionado");
        assertEquals("solucionado", r.getStatus());
    }

    @Test
    @DisplayName("UniqueFace: constructor completo y setters")
    void uniqueFaceStoresValues() {
        UniqueFace u = new UniqueFace("f1", "url", DATE, 3, true);
        assertEquals("f1", u.getFaceId());
        assertEquals("url", u.getImageUrl());
        assertEquals(DATE, u.getCapturedAt());
        assertEquals(3, u.getReportCount());
        assertTrue(u.isSuspect());

        UniqueFace empty = new UniqueFace();
        empty.setFaceId("f2"); empty.setImageUrl("u2"); empty.setCapturedAt(DATE);
        empty.setReportCount(1); empty.setSuspect(false);
        assertEquals("f2", empty.getFaceId());
        assertEquals("u2", empty.getImageUrl());
        assertEquals(1, empty.getReportCount());
        assertFalse(empty.isSuspect());
    }

    @Test
    @DisplayName("VideoResultRequest: records anidados exponen sus campos")
    void videoResultRequestRecords() {
        var item = new VideoResultRequest.FaceAppearanceResult("f1", "00:00:01", "url", "2026-10-05T10:00:00Z");
        var request = new VideoResultRequest(List.of(item));
        assertEquals(1, request.appearances().size());
        assertEquals("f1", item.faceId());
        assertEquals("00:00:01", item.timestamp());
        assertEquals("url", item.frameUrl());
        assertEquals("2026-10-05T10:00:00Z", item.capturedAt());
        assertEquals(item, request.appearances().get(0));
    }
}
