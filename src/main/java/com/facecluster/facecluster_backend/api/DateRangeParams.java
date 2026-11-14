package com.facecluster.facecluster_backend.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

public final class DateRangeParams {

    private DateRangeParams() {
    }

    public record DateRange(LocalDateTime from, LocalDateTime to) {

        public boolean isEmpty() {
            return from == null && to == null;
        }

        public boolean hasFrom() {
            return from != null;
        }

        public boolean hasTo() {
            return to != null;
        }
    }

    public static DateRange parse(String startDate, String endDate) {
        LocalDate start = parseDay(startDate);
        LocalDate end = parseDay(endDate);

        if (start != null && end != null && start.isAfter(end)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La fecha inicial es posterior a la fecha final"
            );
        }

        LocalDateTime from = start != null ? start.atStartOfDay() : null;
        LocalDateTime to = end != null ? end.atTime(LocalTime.MAX) : null;
        return new DateRange(from, to);
    }

    private static LocalDate parseDay(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Fecha invalida: " + value + ". Formato esperado AAAA-MM-DD"
            );
        }
    }
}
