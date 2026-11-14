package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FaceImageServiceTest {

    private static final String BUCKET = "bucket";

    private S3Client s3Client;
    private S3Presigner s3Presigner;

    @BeforeEach
    void setUp() throws Exception {
        s3Client = mock(S3Client.class);
        s3Presigner = mock(S3Presigner.class);

        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://presigned.example/url").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presigned);
    }

    private FaceImageService serviceWithPrefix(String prefix) {
        return new FaceImageService(s3Client, s3Presigner, BUCKET, prefix, 15);
    }

    private void s3Contains(String... keys) {
        ListObjectsV2Iterable iterable = mock(ListObjectsV2Iterable.class);
        List<S3Object> objects = java.util.Arrays.stream(keys)
                .map(key -> S3Object.builder().key(key).build())
                .toList();
        when(iterable.contents()).thenReturn(objects::iterator);
        when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
                .thenReturn(iterable);
    }

    private FaceAppearance appearance(String faceId, String key) {
        FaceAppearance appearance = new FaceAppearance();
        appearance.setId(faceId);
        appearance.setJobId("job-1");
        appearance.setFaceId(faceId);
        appearance.setFrameUrl(
                "https://" + BUCKET + ".s3.us-east-1.amazonaws.com/" + key
        );
        return appearance;
    }

    @Test
    void listsOnlyTheJobPrefixAndDropsMissingImages() {
        s3Contains("faces/job-1/face_1.jpg");
        FaceAppearance present = appearance("job-1_1", "faces/job-1/face_1.jpg");
        FaceAppearance missing = appearance("job-1_2", "faces/job-1/face_2.jpg");

        List<FaceAppearance> result = serviceWithPrefix("faces/")
                .withS3ImagesForJob("job-1", List.of(present, missing));

        ArgumentCaptor<ListObjectsV2Request> captor =
                ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2Paginator(captor.capture());
        assertThat(captor.getValue().prefix()).isEqualTo("faces/job-1/");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFaceId()).isEqualTo("job-1_1");
        assertThat(result.get(0).getJobId()).isEqualTo("job-1");
        assertThat(result.get(0).getFrameUrl()).isEqualTo("https://presigned.example/url");
    }

    @Test
    void addsTheMissingSlashToTheConfiguredPrefix() {
        s3Contains();

        serviceWithPrefix("faces").withS3ImagesForJob("job-1", List.of());

        ArgumentCaptor<ListObjectsV2Request> captor =
                ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2Paginator(captor.capture());
        assertThat(captor.getValue().prefix()).isEqualTo("faces/job-1/");
    }

    @Test
    void listsTheWholePrefixForTheGeneralMethod() {
        s3Contains("faces/job-1/face_1.jpg");

        List<FaceAppearance> result = serviceWithPrefix("faces/").withS3Images(
                List.of(appearance("job-1_1", "faces/job-1/face_1.jpg"))
        );

        ArgumentCaptor<ListObjectsV2Request> captor =
                ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2Paginator(captor.capture());
        assertThat(captor.getValue().prefix()).isEqualTo("faces/");
        assertThat(result).hasSize(1);
    }

    @Test
    void groupsByJobAndListsOnlyTheInvolvedJobPrefixes() {
        s3Contains("faces/job-1/face_1.jpg", "faces/job-2/face_1.jpg");
        FaceAppearance first = appearance("job-1_1", "faces/job-1/face_1.jpg");
        FaceAppearance second = appearance("job-2_1", "faces/job-2/face_1.jpg");
        second.setJobId("job-2");

        List<FaceAppearance> result = serviceWithPrefix("faces/")
                .withS3ImagesByJob(List.of(first, second));

        ArgumentCaptor<ListObjectsV2Request> captor =
                ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client, times(2)).listObjectsV2Paginator(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ListObjectsV2Request::prefix)
                .containsExactly("faces/job-1/", "faces/job-2/");
        assertThat(result).extracting(FaceAppearance::getFaceId)
                .containsExactly("job-1_1", "job-2_1");
    }

    @Test
    void skipsAppearancesWithoutJobIdWhenGrouping() {
        FaceAppearance orphan = appearance("job-1_1", "faces/job-1/face_1.jpg");
        orphan.setJobId(null);

        List<FaceAppearance> result = serviceWithPrefix("faces/")
                .withS3ImagesByJob(List.of(orphan));

        assertThat(result).isEmpty();
        verify(s3Client, never()).listObjectsV2Paginator(any(ListObjectsV2Request.class));
    }
}
