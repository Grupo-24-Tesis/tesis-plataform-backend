package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.domain.Guard;
import com.facecluster.facecluster_backend.repository.GuardRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/guards")
public class GuardController {
    private final GuardRepository repo;
    public GuardController(GuardRepository repo) { this.repo = repo; }

    @GetMapping public List<Guard> all() { return repo.findAll(); }
    @PostMapping public Guard create(@RequestBody Guard body) {
        body.setId(null);
        return repo.save(body);
    }
}