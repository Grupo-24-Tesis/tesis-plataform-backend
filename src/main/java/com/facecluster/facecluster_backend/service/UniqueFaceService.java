package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.domain.MarkedPerson;
import com.facecluster.facecluster_backend.domain.ReportItem;
import com.facecluster.facecluster_backend.dto.UniqueFace;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.MarkedRepository;
import com.facecluster.facecluster_backend.repository.ReportRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UniqueFaceService {

    private final FaceAppearanceRepository faceAppearanceRepository;
    private final ReportRepository reportRepository;
    private final MarkedRepository markedRepository;

    public UniqueFaceService(
            FaceAppearanceRepository faceAppearanceRepository,
            ReportRepository reportRepository,
            MarkedRepository markedRepository
    ) {
        this.faceAppearanceRepository = faceAppearanceRepository;
        this.reportRepository = reportRepository;
        this.markedRepository = markedRepository;
    }

    public List<UniqueFace> getUniqueFaces() {

        List<FaceAppearance> appearances =
                faceAppearanceRepository.findAll();

        List<ReportItem> reports =
                reportRepository.findAll();

        List<MarkedPerson> markedPeople =
                markedRepository.findAll();


        // FaceIds que están marcados como sospechosos
        Set<String> suspectFaceIds = markedPeople.stream()
                .map(MarkedPerson::getFaceId)
                .collect(Collectors.toSet());


        // Contamos en cuántos reportes aparece cada faceId
        Map<String, Long> reportCounts = new HashMap<>();

        for (ReportItem report : reports) {

            if (report.getFaceIds() == null) {
                continue;
            }

            // Evitamos contar dos veces la misma persona
            // si accidentalmente aparece repetida dentro del mismo reporte
            Set<String> uniqueFaceIds =
                    new HashSet<>(report.getFaceIds());

            for (String faceId : uniqueFaceIds) {

                reportCounts.merge(
                        faceId,
                        1L,
                        Long::sum
                );
            }
        }


        // Agrupamos las apariciones por faceId
        Map<String, FaceAppearance> representativeFaces =
                appearances.stream()
                        .collect(Collectors.toMap(
                                FaceAppearance::getFaceId,
                                appearance -> appearance,
                                (existing, replacement) -> existing
                        ));


        List<UniqueFace> result = new ArrayList<>();


        for (Map.Entry<String, FaceAppearance> entry :
                representativeFaces.entrySet()) {

            String faceId = entry.getKey();

            FaceAppearance appearance =
                    entry.getValue();

            int reportCount =
                    reportCounts.getOrDefault(faceId, 0L).intValue();

            boolean suspect =
                    suspectFaceIds.contains(faceId);


            result.add(
                    new UniqueFace(
                            faceId,
                            appearance.getFrameUrl(),
                            appearance.getCapturedAt(),
                            reportCount,
                            suspect
                    )
            );
        }

        return result;
    }
}