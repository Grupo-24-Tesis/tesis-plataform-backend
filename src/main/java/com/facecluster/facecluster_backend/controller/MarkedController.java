package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.domain.MarkedPerson;
import com.facecluster.facecluster_backend.repository.MarkedRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/marked")
@CrossOrigin(origins = "*")
public class MarkedController {
    private final MarkedRepository repo;
    public MarkedController(MarkedRepository repo) { this.repo = repo; }

    @GetMapping public List<MarkedPerson> all() { return repo.findAll(); }
    @PostMapping public MarkedPerson create(@RequestBody MarkedPerson body) { return repo.save(body); }
}