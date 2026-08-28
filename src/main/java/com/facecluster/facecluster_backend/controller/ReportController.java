package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.domain.ReportItem;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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

        // Fecha de creación automática
        body.setCreatedAt(LocalDateTime.now());

        // incidentDate viene desde el frontend
        return repo.save(body);
    }

    @PutMapping("/{id}/status")
    public ReportItem updateStatus(
            @PathVariable String id,
            @RequestBody StatusRequest request
    ) {

        ReportItem report = repo.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Reporte no encontrado"));

        report.setStatus(request.status());

        // Si se está resolviendo el reporte,
        // también guardamos la fecha y descripción
        if ("solucionado".equals(request.status())) {

            report.setResolvedAt(request.resolvedAt());

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