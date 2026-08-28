package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.MarkedRepository;
import com.facecluster.facecluster_backend.repository.ReportRepository;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/face-appearances")
@CrossOrigin(origins = "*")
public class FaceAppearanceController {

    private final FaceAppearanceRepository repository;
    private final ReportRepository reportRepository;
    private final MarkedRepository markedRepository;

    public FaceAppearanceController(
            FaceAppearanceRepository repository,
            ReportRepository reportRepository,
            MarkedRepository markedRepository
    ) {
        this.repository = repository;
        this.reportRepository = reportRepository;
        this.markedRepository = markedRepository;
    }


    // ==========================================
    // OBTENER TODAS LAS APARICIONES
    // ==========================================

    @GetMapping
    public List<FaceAppearance> findAll() {
        return repository.findAll();
    }


    // ==========================================
    // OBTENER CARAS ÚNICAS
    // ==========================================

    @GetMapping("/unique")
    public List<UniqueFaceResponse> findUniqueFaces() {

        List<FaceAppearance> appearances = repository.findAll();

        Map<String, List<FaceAppearance>> grouped =
                new LinkedHashMap<>();

        // Agrupar apariciones por faceId
        for (FaceAppearance appearance : appearances) {

            grouped
                    .computeIfAbsent(
                            appearance.getFaceId(),
                            key -> new ArrayList<>()
                    )
                    .add(appearance);
        }

        List<UniqueFaceResponse> response =
                new ArrayList<>();

        // Crear respuesta para cada persona
        for (Map.Entry<String, List<FaceAppearance>> entry
                : grouped.entrySet()) {

            String faceId = entry.getKey();

            List<FaceAppearance> faceAppearances =
                    entry.getValue();


            // ==========================================
            // CONTAR REPORTES
            // ==========================================

            int reportCount =
                    reportRepository
                            .findByFaceIdsContaining(faceId)
                            .size();


            // ==========================================
            // VERIFICAR SI ES SOSPECHOSO
            // ==========================================

            boolean suspect =
                    markedRepository
                            .findByFaceId(faceId)
                            .isPresent();


            // ==========================================
            // AGREGAR PERSONA A LA RESPUESTA
            // ==========================================

            response.add(
                    new UniqueFaceResponse(
                            faceId,
                            faceAppearances,
                            reportCount,
                            suspect
                    )
            );
        }

        return response;
    }


    // ==========================================
    // CREAR APARICIÓN
    // ==========================================

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FaceAppearance create(
            @RequestBody FaceAppearance body
    ) {

        return repository.save(body);
    }


    // ==========================================
    // RESPUESTA PARA CARAS ÚNICAS
    // ==========================================

    public record UniqueFaceResponse(

            String faceId,

            List<FaceAppearance> appearances,

            int reportCount,

            boolean suspect

    ) {}
}