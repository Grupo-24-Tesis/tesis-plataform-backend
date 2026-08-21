package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.domain.ReportItem;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
        return repo.save(body);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ReportItem> updateStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body
    ) {
        return repo.findById(id)
                .map(report -> {
                    report.setStatus(body.get("status"));
                    return ResponseEntity.ok(repo.save(report));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}