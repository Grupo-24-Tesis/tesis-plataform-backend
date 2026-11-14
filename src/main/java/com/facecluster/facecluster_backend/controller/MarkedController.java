package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.domain.MarkedPerson;
import com.facecluster.facecluster_backend.repository.GuardRepository;
import com.facecluster.facecluster_backend.repository.MarkedRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/marked")
public class MarkedController {

    private final MarkedRepository repo;

    private final GuardRepository guardRepository;

    public MarkedController(MarkedRepository repo, GuardRepository guardRepository) {
        this.repo = repo;
        this.guardRepository = guardRepository;
    }

    @GetMapping
    public List<MarkedPerson> all() {
        return repo.findAll();
    }

    @PostMapping
    public MarkedPerson create(@RequestBody MarkedPerson body) {
        if (body.getFaceId() == null || body.getFaceId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "faceId es obligatorio");
        }

        if (body.getCreatedByGuardId() != null && !guardRepository.existsById(body.getCreatedByGuardId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El vigilante no existe");
        }

        if (repo.existsByFaceId(body.getFaceId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La detección ya está marcada");
        }

        body.setId(null);
        return repo.save(body);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        repo.deleteById(id);
    }
}