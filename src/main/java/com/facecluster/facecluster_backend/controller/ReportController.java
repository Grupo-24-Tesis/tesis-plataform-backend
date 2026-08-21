package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.domain.ReportItem;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportController {
    private final ReportRepository repo;
    public ReportController(ReportRepository repo) { this.repo = repo; }

    @GetMapping public List<ReportItem> all() { return repo.findAll(); }
    @PostMapping public ReportItem create(@RequestBody ReportItem body) { return repo.save(body); }
}