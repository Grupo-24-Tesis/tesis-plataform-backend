package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.MarkedRepository;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import com.facecluster.facecluster_backend.service.FaceImageService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
private final FaceImageService faceImageService;

public FaceAppearanceController(
        FaceAppearanceRepository repository,
        ReportRepository reportRepository,
        MarkedRepository markedRepository,
        FaceImageService faceImageService
) {
    this.repository = repository;
    this.reportRepository = reportRepository;
    this.markedRepository = markedRepository;
    this.faceImageService = faceImageService;
}


// ==========================================
// OBTENER TODAS LAS APARICIONES
// ==========================================

@GetMapping
public List<FaceAppearance> findAll() {
    // Solo apariciones cuya imagen sigue en S3, con URL prefirmada
    return faceImageService.withS3Images(repository.findAll());
}


// ==========================================
// OBTENER CARAS ÚNICAS CON FILTROS
// ==========================================

@GetMapping("/unique")
public List<UniqueFaceResponse> findUniqueFaces(

        @RequestParam(required = false)
        String startDate,

        @RequestParam(required = false)
        String endDate,

        @RequestParam(required = false)
        Boolean hasReports,

        @RequestParam(required = false)
        Boolean suspect
) {

    // ==========================================
    // OBTENER APARICIONES
    // ==========================================

    List<FaceAppearance> appearances;


    // ==========================================
    // FILTRO DE FECHA
    // ==========================================

    if (startDate != null &&
        !startDate.isBlank() &&
        endDate != null &&
        !endDate.isBlank()) {

        LocalDateTime start =
                LocalDate.parse(startDate)
                        .atStartOfDay();

        LocalDateTime end =
                LocalDate.parse(endDate)
                        .atTime(LocalTime.MAX);

        appearances =
                repository.findByCapturedAtBetween(
                        start,
                        end
                );

    } else {

        // Sin filtro de fecha
        appearances = repository.findAll();
    }


    // ==========================================
    // SOLO IMÁGENES QUE EXISTEN EN S3
    // ==========================================

    // Las personas sin ninguna imagen en el bucket no se muestran
    appearances = faceImageService.withS3Images(appearances);


    // ==========================================
    // AGRUPAR POR FACE ID
    // ==========================================

    Map<String, List<FaceAppearance>> grouped =
            new LinkedHashMap<>();

    for (FaceAppearance appearance : appearances) {

        grouped
                .computeIfAbsent(
                        appearance.getFaceId(),
                        key -> new ArrayList<>()
                )
                .add(appearance);
    }


    // ==========================================
    // CREAR RESPUESTA
    // ==========================================

    List<UniqueFaceResponse> response =
            new ArrayList<>();


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
        // VERIFICAR SOSPECHOSO
        // ==========================================

        boolean isSuspect =
                markedRepository
                        .findByFaceId(faceId)
                        .isPresent();


        // ==========================================
        // FILTRO: REPORTES
        // ==========================================

        if (hasReports != null) {

            boolean hasAnyReports =
                    reportCount > 0;

            if (hasReports != hasAnyReports) {
                continue;
            }
        }


        // ==========================================
        // FILTRO: SOSPECHOSO
        // ==========================================

        if (suspect != null) {

            if (suspect != isSuspect) {
                continue;
            }
        }


        // ==========================================
        // AGREGAR PERSONA
        // ==========================================

        response.add(
                new UniqueFaceResponse(
                        faceId,
                        faceAppearances,
                        reportCount,
                        isSuspect
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
