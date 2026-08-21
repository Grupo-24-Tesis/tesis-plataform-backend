package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.domain.Camera;
import com.facecluster.facecluster_backend.repository.CameraRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/cameras")
@CrossOrigin(origins = "*")
public class CameraController {
    private final CameraRepository repo;
    public CameraController(CameraRepository repo) { this.repo = repo; }

    @GetMapping public List<Camera> all() { return repo.findAll(); }
    @PostMapping public Camera create(@RequestBody Camera body) { return repo.save(body); }
}