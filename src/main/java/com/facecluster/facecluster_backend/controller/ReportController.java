package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.api.DateRangeParams;
import com.facecluster.facecluster_backend.api.DateRangeParams.DateRange;
import com.facecluster.facecluster_backend.domain.ReportItem;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import org.springframework.data.domain.Range;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final String STATUS_INVESTIGATION = "investigacion";
    private static final String STATUS_SOLVED = "solucionado";

    private final ReportRepository repo;
    private final Clock clock;

    public ReportController(ReportRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }


    @GetMapping
    public List<ReportItem> all() {
        return repo.findAll();
    }


    @PostMapping
    public ReportItem create(@RequestBody ReportItem body) {

        validateStatus(body.getStatus());

        if (STATUS_SOLVED.equals(body.getStatus())) {
            if (body.getResolvedAt() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Un reporte solucionado necesita fecha de solucion"
                );
            }
        } else {
            body.setResolvedAt(null);
            body.setResolutionDescription(null);
        }

        body.setId(null);
        body.setCreatedAt(LocalDateTime.now(clock));

        return repo.save(body);
    }


    @GetMapping("/filter")
    public List<ReportItem> filter(

            @RequestParam(required = false)
            String status,

            @RequestParam(required = false)
            String date,

            @RequestParam(required = false)
            String startDate,

            @RequestParam(required = false)
            String endDate,

            @RequestParam(required = false)
            String dateType

    ) {

        if (status == null &&
            date == null &&
            startDate == null &&
            endDate == null) {

            return repo.findAll();
        }


        boolean hasDate = date != null && !date.isBlank();

        DateRange range = hasDate
                ? DateRangeParams.parse(date, date)
                : DateRangeParams.parse(startDate, endDate);

        boolean hasStatus = status != null && !status.isBlank();

        boolean resolutionDate =
                "resolucion".equalsIgnoreCase(dateType);

        if (range.isEmpty()) {

            return hasStatus ? repo.findByStatus(status) : repo.findAll();
        }

        return resolutionDate
                ? filterByResolvedAt(hasStatus ? status : null, range)
                : filterByIncidentDate(hasStatus ? status : null, range);
    }


    private List<ReportItem> filterByIncidentDate(String status, DateRange range) {

        if (range.hasFrom() && range.hasTo()) {

            return status != null
                    ? repo.findByStatusAndIncidentDateBetween(status, Range.closed(range.from(), range.to()))
                    : repo.findByIncidentDateBetween(Range.closed(range.from(), range.to()));
        }

        if (range.hasFrom()) {

            return status != null
                    ? repo.findByStatusAndIncidentDateGreaterThanEqual(status, range.from())
                    : repo.findByIncidentDateGreaterThanEqual(range.from());
        }

        return status != null
                ? repo.findByStatusAndIncidentDateLessThanEqual(status, range.to())
                : repo.findByIncidentDateLessThanEqual(range.to());
    }


    private List<ReportItem> filterByResolvedAt(String status, DateRange range) {

        if (range.hasFrom() && range.hasTo()) {

            return status != null
                    ? repo.findByStatusAndResolvedAtBetween(status, Range.closed(range.from(), range.to()))
                    : repo.findByResolvedAtBetween(Range.closed(range.from(), range.to()));
        }

        if (range.hasFrom()) {

            return status != null
                    ? repo.findByStatusAndResolvedAtGreaterThanEqual(status, range.from())
                    : repo.findByResolvedAtGreaterThanEqual(range.from());
        }

        return status != null
                ? repo.findByStatusAndResolvedAtLessThanEqual(status, range.to())
                : repo.findByResolvedAtLessThanEqual(range.to());
    }


    @PutMapping("/{id}/status")
    public ReportItem updateStatus(

            @PathVariable String id,

            @RequestBody StatusRequest request

    ) {

        validateStatus(request.status());

        ReportItem report =
                repo.findById(id)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Reporte no encontrado"
                                )
                        );


        report.setStatus(
                request.status()
        );


        if (STATUS_SOLVED.equals(request.status())) {

            report.setResolvedAt(
                    request.resolvedAt()
            );

            report.setResolutionDescription(
                    request.resolutionDescription()
            );
        } else {

            report.setResolvedAt(null);

            report.setResolutionDescription(null);
        }


        return repo.save(report);
    }


    private void validateStatus(String status) {

        if (!STATUS_INVESTIGATION.equals(status) && !STATUS_SOLVED.equals(status)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Estado de reporte no valido"
            );
        }
    }


    public record StatusRequest(

            String status,

            LocalDateTime resolvedAt,

            String resolutionDescription

    ) {}
}