package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.domain.ReportItem;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
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


        LocalDateTime start = null;
        LocalDateTime end = null;


        if (date != null && !date.isBlank()) {

            LocalDate selectedDate =
                    LocalDate.parse(date);

            start =
                    selectedDate.atStartOfDay();

            end =
                    selectedDate.atTime(LocalTime.MAX);
        }


        else if (
                startDate != null &&
                !startDate.isBlank() &&
                endDate != null &&
                !endDate.isBlank()
        ) {

            start =
                    LocalDate.parse(startDate)
                            .atStartOfDay();

            end =
                    LocalDate.parse(endDate)
                            .atTime(LocalTime.MAX);
        }


        boolean resolutionDate =
                "resolucion".equalsIgnoreCase(dateType);


        if (
                status != null &&
                !status.isBlank() &&
                start != null &&
                end != null
        ) {

            if (resolutionDate) {

                return repo.findByStatusAndResolvedAtBetween(
                        status,
                        start,
                        end
                );

            } else {

                return repo.findByStatusAndIncidentDateBetween(
                        status,
                        start,
                        end
                );
            }
        }


        if (
                status != null &&
                !status.isBlank()
        ) {

            return repo.findByStatus(status);
        }


        if (
                start != null &&
                end != null
        ) {

            if (resolutionDate) {

                return repo.findByResolvedAtBetween(
                        start,
                        end
                );

            } else {

                return repo.findByIncidentDateBetween(
                        start,
                        end
                );
            }
        }


        return repo.findAll();
    }


    @PutMapping("/{id}/status")
    public ReportItem updateStatus(

            @PathVariable String id,

            @RequestBody StatusRequest request

    ) {

        ReportItem report =
                repo.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
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