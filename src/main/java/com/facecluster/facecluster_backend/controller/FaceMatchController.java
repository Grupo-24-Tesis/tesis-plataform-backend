package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.domain.FaceMatch;
import com.facecluster.facecluster_backend.dto.FaceMatchRequest;
import com.facecluster.facecluster_backend.service.FaceMatchService;
import com.facecluster.facecluster_backend.service.FaceMatchService.DecisionResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/face-matches")
public class FaceMatchController {

    private final FaceMatchService service;

    public FaceMatchController(FaceMatchService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<FaceMatch> decide(@RequestBody FaceMatchRequest request) {
        DecisionResult result = service.decide(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.match());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undo(@PathVariable String id) {
        service.undo(id);
    }
}
