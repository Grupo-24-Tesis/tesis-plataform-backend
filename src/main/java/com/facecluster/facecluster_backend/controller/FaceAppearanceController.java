package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.api.DateRangeParams;
import com.facecluster.facecluster_backend.api.DateRangeParams.DateRange;
import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.MarkedRepository;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import com.facecluster.facecluster_backend.service.FaceImageService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/face-appearances")
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


@GetMapping
public List<FaceAppearance> findAll() {
    return faceImageService.withS3Images(repository.findAll());
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
                        range.from(),
                        range.to()
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


    appearances = faceImageService.withS3Images(appearances);


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


@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public FaceAppearance create(
        @RequestBody FaceAppearance body
) {

    return repository.save(body);
}


public record UniqueFaceResponse(

        String faceId,

        List<FaceAppearance> appearances,

        int reportCount,

        boolean suspect

) {}


}
