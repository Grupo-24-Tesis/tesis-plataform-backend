package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JobFacesServiceTest {

    private final VideoJobService videoJobService = mock(VideoJobService.class);
    private final FaceAppearanceRepository repository = mock(FaceAppearanceRepository.class);
    private final FaceImageService faceImageService = mock(FaceImageService.class);
    private final JobFacesService service =
            new JobFacesService(videoJobService, repository, faceImageService);

    @Test
    void failsWhenTheJobDoesNotExist() {
        when(videoJobService.getJobById("missing"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Trabajo no encontrado"));

        assertThatThrownBy(() -> service.getFaces("missing"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Trabajo no encontrado");

        verifyNoInteractions(repository, faceImageService);
    }

    @Test
    void returnsTheImagesOfTheJob() {
        FaceAppearance stored = new FaceAppearance();
        FaceAppearance presigned = new FaceAppearance();
        when(repository.findByJobIdOrderByTimestampAsc("job-1")).thenReturn(List.of(stored));
        when(faceImageService.withS3ImagesForJob("job-1", List.of(stored)))
                .thenReturn(List.of(presigned));

        assertThat(service.getFaces("job-1")).containsExactly(presigned);
        verify(faceImageService, never()).withS3Images(List.of(stored));
    }
}
