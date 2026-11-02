package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PR-01 – Pruebas unitarias de FaceImageService.
 * S3Client y S3Presigner se simulan con Mockito: no hay llamadas reales a AWS.
 */
@ExtendWith(MockitoExtension.class)
class FaceImageServiceTest {

    private static final String BUCKET = "my-bucket";
    private static final String PREFIX = "faces/";
    private static final String BASE_URL = "https://my-bucket.s3.us-east-2.amazonaws.com/";

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private FaceImageService service;

    @BeforeEach
    void setUp() {
        service = new FaceImageService(s3Client, s3Presigner, BUCKET, PREFIX, 15);
    }

    // ---------- helpers ----------

    private void givenKeysInBucket(String... keys) {
        List<S3Object> objects = Arrays.stream(keys)
                .map(k -> S3Object.builder().key(k).build())
                .toList();

        SdkIterable<S3Object> contents = objects::iterator;
        ListObjectsV2Iterable iterable = mock(ListObjectsV2Iterable.class);
        when(iterable.contents()).thenReturn(contents);
        when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
                .thenReturn(iterable);
    }

    private void givenPresignedUrl(String url) throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create(url).toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presigned);
    }

    private FaceAppearance appearance(String id, String faceId, String frameUrl) {
        FaceAppearance a = new FaceAppearance();
        a.setId(id);
        a.setFaceId(faceId);
        a.setTimestamp("00:00:04");
        a.setCameraId("cam-1");
        a.setCapturedAt(LocalDateTime.of(2026, 10, 5, 14, 30));
        a.setFrameUrl(frameUrl);
        return a;
    }

    // ---------- pruebas ----------

    @Test
    @DisplayName("Devuelve copia con URL prefirmada cuando la imagen existe en S3")
    void returnsCopyWithPresignedUrlWhenImageExists() throws Exception {
        givenKeysInBucket("faces/job/4.jpg");
        givenPresignedUrl("https://signed.example.com/faces/job/4.jpg?sig=abc");
        FaceAppearance original = appearance("a1", "face-1", BASE_URL + "faces/job/4.jpg");

        List<FaceAppearance> result = service.withS3Images(List.of(original));

        assertEquals(1, result.size());
        FaceAppearance copy = result.get(0);
        assertNotSame(original, copy);
        assertEquals("https://signed.example.com/faces/job/4.jpg?sig=abc", copy.getFrameUrl());
        assertEquals("a1", copy.getId());
        assertEquals("face-1", copy.getFaceId());
        assertEquals("00:00:04", copy.getTimestamp());
        assertEquals("cam-1", copy.getCameraId());
        assertEquals(LocalDateTime.of(2026, 10, 5, 14, 30), copy.getCapturedAt());
    }

    @Test
    @DisplayName("No modifica la entidad original")
    void doesNotMutateOriginalEntity() throws Exception {
        givenKeysInBucket("faces/job/4.jpg");
        givenPresignedUrl("https://signed.example.com/x");
        String originalUrl = BASE_URL + "faces/job/4.jpg";
        FaceAppearance original = appearance("a1", "face-1", originalUrl);

        service.withS3Images(List.of(original));

        assertEquals(originalUrl, original.getFrameUrl());
    }

    @Test
    @DisplayName("Descarta apariciones cuya imagen ya no existe en el bucket")
    void discardsAppearancesWhoseImageIsGone() throws Exception {
        givenKeysInBucket("faces/job/4.jpg");
        givenPresignedUrl("https://signed.example.com/x");
        FaceAppearance kept = appearance("a1", "face-1", BASE_URL + "faces/job/4.jpg");
        FaceAppearance removed = appearance("a2", "face-2", BASE_URL + "faces/job/borrada.jpg");

        List<FaceAppearance> result = service.withS3Images(List.of(kept, removed));

        assertEquals(1, result.size());
        assertEquals("a1", result.get(0).getId());
    }

    @ParameterizedTest(name = "URL descartada: [{0}]")
    @NullSource
    @ValueSource(strings = {
            "",
            "   ",
            "https://otro-bucket.s3.us-east-2.amazonaws.com/faces/a.jpg",
            "https://example.com/faces/a.jpg",
            "https://my-bucket.s3.us-east-2.amazonaws.com/",
            "https://my-bucket.s3.us-east-2.amazonaws.com",
            "ht tp://url invalida"
    })
    void discardsAppearancesWithInvalidOrForeignUrls(String frameUrl) {
        givenKeysInBucket("faces/a.jpg");

        List<FaceAppearance> result =
                service.withS3Images(List.of(appearance("a1", "face-1", frameUrl)));

        assertTrue(result.isEmpty());
        verifyNoInteractions(s3Presigner);
    }

    @Test
    @DisplayName("Decodifica la key del objeto cuando la URL viene codificada")
    void decodesUrlEncodedObjectKey() throws Exception {
        givenKeysInBucket("faces/job 1/a b.jpg");
        givenPresignedUrl("https://signed.example.com/x");
        FaceAppearance a = appearance("a1", "face-1", BASE_URL + "faces/job%201/a%20b.jpg");

        List<FaceAppearance> result = service.withS3Images(List.of(a));

        assertEquals(1, result.size());
        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertEquals("faces/job 1/a b.jpg", captor.getValue().getObjectRequest().key());
    }

    @Test
    @DisplayName("La URL prefirmada usa el bucket y la duración configurados")
    void presignedRequestUsesConfiguredBucketAndDuration() throws Exception {
        givenKeysInBucket("faces/a.jpg");
        givenPresignedUrl("https://signed.example.com/x");

        service.withS3Images(List.of(appearance("a1", "f", BASE_URL + "faces/a.jpg")));

        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());
        assertEquals(BUCKET, captor.getValue().getObjectRequest().bucket());
        assertEquals(Duration.ofMinutes(15), captor.getValue().signatureDuration());
    }

    @Test
    @DisplayName("Lista las imágenes usando el bucket y el prefijo configurados")
    void listsObjectsWithConfiguredBucketAndPrefix() {
        givenKeysInBucket();

        service.withS3Images(List.of());

        ArgumentCaptor<ListObjectsV2Request> captor =
                ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2Paginator(captor.capture());
        assertEquals(BUCKET, captor.getValue().bucket());
        assertEquals(PREFIX, captor.getValue().prefix());
    }

    @Test
    @DisplayName("Lista vacía de entrada produce lista vacía")
    void emptyInputReturnsEmptyList() {
        givenKeysInBucket("faces/a.jpg");

        assertTrue(service.withS3Images(List.of()).isEmpty());
    }

    @Test
    @DisplayName("Si S3 falla responde 503 SERVICE_UNAVAILABLE")
    void s3FailureProduces503() {
        when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class)))
                .thenThrow(new RuntimeException("S3 caído"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.withS3Images(List.of(appearance("a1", "f", BASE_URL + "faces/a.jpg")))
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getStatusCode().value());
        assertEquals("No fue posible consultar las imágenes en S3", ex.getReason());
    }
}
