package com.facecluster.facecluster_backend.api;

import com.facecluster.facecluster_backend.api.DateRangeParams.DateRange;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateRangeParamsTest {

    @Test
    void conAmbosLimitesDevuelveElRangoInclusivo() {
        DateRange range = DateRangeParams.parse("2026-11-01", "2026-11-14");

        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), range.from());
        assertEquals(LocalDateTime.of(2026, 11, 14, 0, 0).with(LocalTime.MAX), range.to());
    }

    @Test
    void soloFechaInicialNoTieneLimiteSuperior() {
        DateRange range = DateRangeParams.parse("2026-11-01", null);

        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), range.from());
        assertNull(range.to());
    }

    @Test
    void soloFechaFinalNoTieneLimiteInferior() {
        DateRange range = DateRangeParams.parse(null, "2026-11-14");

        assertNull(range.from());
        assertEquals(LocalDateTime.of(2026, 11, 14, 0, 0).with(LocalTime.MAX), range.to());
    }

    @Test
    void sinFechasNoFiltra() {
        assertTrue(DateRangeParams.parse(null, null).isEmpty());
    }

    @Test
    void cadenasEnBlancoSeTratanComoAusentes() {
        assertTrue(DateRangeParams.parse("  ", "").isEmpty());
    }

    @Test
    void fechaMalFormadaResponde400ConMensaje() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> DateRangeParams.parse("14/11/2026", null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("Fecha invalida: 14/11/2026. Formato esperado AAAA-MM-DD", exception.getReason());
    }

    @Test
    void fechaInicialPosteriorALaFinalResponde400() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> DateRangeParams.parse("2026-11-15", "2026-11-14")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("La fecha inicial es posterior a la fecha final", exception.getReason());
    }
}
