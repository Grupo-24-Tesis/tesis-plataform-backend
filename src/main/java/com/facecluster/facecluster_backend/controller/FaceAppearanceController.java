package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/face-appearances")
@CrossOrigin(origins = "*")
public class FaceAppearanceController {

    private final FaceAppearanceRepository repository;

    public FaceAppearanceController(FaceAppearanceRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<FaceAppearance> findAll() {
        return repository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FaceAppearance create(@RequestBody FaceAppearance body) {
        return repository.save(body);
    }
}