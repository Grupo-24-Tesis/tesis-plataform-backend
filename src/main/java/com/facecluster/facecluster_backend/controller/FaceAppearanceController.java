package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.api.DateRangeParams;
import com.facecluster.facecluster_backend.api.DateRangeParams.DateRange;
import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.MarkedRepository;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import com.facecluster.facecluster_backend.dto.DetectionMatchesResponse;
import com.facecluster.facecluster_backend.dto.MatchDetectionResponse;
import com.facecluster.facecluster_backend.service.CandidateService;
import com.facecluster.facecluster_backend.service.FaceImageService;
import com.facecluster.facecluster_backend.service.FaceMatchService;

import org.springframework.data.domain.Range;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/face-appearances")
public class FaceAppearanceController {

private static final int MAX_BY_IDS = 50;


private final FaceAppearanceRepository repository;
private final ReportRepository reportRepository;
private final MarkedRepository markedRepository;
private final FaceImageService faceImageService;
private final FaceMatchService faceMatchService;
private final CandidateService candidateService;

public FaceAppearanceController(
        FaceAppearanceRepository repository,
        ReportRepository reportRepository,
        MarkedRepository markedRepository,
        FaceImageService faceImageService,
        FaceMatchService faceMatchService,
        CandidateService candidateService
) {
    this.repository = repository;
    this.reportRepository = reportRepository;
    this.markedRepository = markedRepository;
    this.faceImageService = faceImageService;
    this.faceMatchService = faceMatchService;
    this.candidateService = candidateService;
}


@GetMapping
public List<FaceAppearance> findAll() {
    return faceMatchService.withMatchCounts(
            faceImageService.withS3Images(repository.findAll())
    );
}


@GetMapping("/by-ids")
public List<FaceAppearance> findByIds(@RequestParam List<String> faceIds) {

    List<String> ids =
            faceIds.stream()
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .distinct()
                    .toList();

    if (ids.size() > MAX_BY_IDS) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Se permiten como máximo " + MAX_BY_IDS + " detecciones por consulta"
        );
    }

    return faceMatchService.withMatchCounts(
            faceImageService.withS3ImagesByJob(repository.findAllById(ids))
    );
}


@GetMapping("/{faceId}/matches")
public DetectionMatchesResponse matches(@PathVariable String faceId) {
    return faceMatchService.group(faceId);
}


@GetMapping("/{faceId}/candidates")
public List<MatchDetectionResponse> candidates(
        @PathVariable String faceId,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Double minScore
) {
    return candidateService.candidates(faceId, limit, minScore);
}


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

    DateRange range =
            DateRangeParams.parse(startDate, endDate);

    List<FaceAppearance> appearances;

    if (range.hasFrom() && range.hasTo()) {

        appearances =
                repository.findByCapturedAtBetween(
                        Range.closed(range.from(), range.to())
                );

    } else if (range.hasFrom()) {

        appearances =
                repository.findByCapturedAtGreaterThanEqual(
                        range.from()
                );

    } else if (range.hasTo()) {

        appearances =
                repository.findByCapturedAtLessThanEqual(
                        range.to()
                );

    } else {

        appearances = repository.findAll();
    }


    appearances = faceMatchService.withMatchCounts(
            faceImageService.withS3Images(appearances)
    );


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


    List<UniqueFaceResponse> response =
            new ArrayList<>();


    for (Map.Entry<String, List<FaceAppearance>> entry
            : grouped.entrySet()) {

        String faceId = entry.getKey();

        List<FaceAppearance> faceAppearances =
                entry.getValue();


        int reportCount =
                reportRepository
                        .findByFaceIdsContaining(faceId)
                        .size();


        boolean isSuspect =
                markedRepository
                        .existsByFaceId(faceId);


        if (hasReports != null) {

            boolean hasAnyReports =
                    reportCount > 0;

            if (hasReports != hasAnyReports) {
                continue;
            }
        }


        if (suspect != null) {

            if (suspect != isSuspect) {
                continue;
            }
        }


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


public record UniqueFaceResponse(

        String faceId,

        List<FaceAppearance> appearances,

        int reportCount,

        boolean suspect

) {}


}
