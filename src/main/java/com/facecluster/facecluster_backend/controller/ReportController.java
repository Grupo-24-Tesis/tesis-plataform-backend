package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.api.DateRangeParams;
import com.facecluster.facecluster_backend.api.DateRangeParams.DateRange;
import com.facecluster.facecluster_backend.domain.ReportItem;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportRepository repo;

    public ReportController(ReportRepository repo) {
        this.repo = repo;
    }


    @GetMapping
    public List<ReportItem> all() {
        return repo.findAll();
    }


    @PostMapping
    public ReportItem create(@RequestBody ReportItem body) {

        body.setCreatedAt(LocalDateTime.now());

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
                    ? repo.findByStatusAndIncidentDateBetween(status, range.from(), range.to())
                    : repo.findByIncidentDateBetween(range.from(), range.to());
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
                    ? repo.findByStatusAndResolvedAtBetween(status, range.from(), range.to())
                    : repo.findByResolvedAtBetween(range.from(), range.to());
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


        if (
                "solucionado"
                        .equals(request.status())
        ) {

            report.setResolvedAt(
                    request.resolvedAt()
            );

            report.setResolutionDescription(
                    request.resolutionDescription()
            );
        }


        return repo.save(report);
    }


    public record StatusRequest(

            String status,

            LocalDateTime resolvedAt,

            String resolutionDescription

    ) {}
}