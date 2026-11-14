package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JobFacesService {

    private final VideoJobService videoJobService;
    private final FaceAppearanceRepository faceAppearanceRepository;
    private final FaceImageService faceImageService;

    public JobFacesService(
            VideoJobService videoJobService,
            FaceAppearanceRepository faceAppearanceRepository,
            FaceImageService faceImageService
    ) {
        this.videoJobService = videoJobService;
        this.faceAppearanceRepository = faceAppearanceRepository;
        this.faceImageService = faceImageService;
    }

    public List<FaceAppearance> getFaces(String jobId) {
        videoJobService.getJobById(jobId);

        return faceImageService.withS3ImagesForJob(
                jobId,
                faceAppearanceRepository.findByJobIdOrderByTimestampAsc(jobId)
        );
    }
}
