package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.dto.PresignedUploadRequest;
import com.facecluster.facecluster_backend.dto.PresignedUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PR-01 – Pruebas unitarias de S3UploadService (validaciones y generación de URL prefirmada).
 */
@ExtendWith(MockitoExtension.class)
class S3UploadServiceTest {

    private static final String BUCKET = "video-bucket";
    private static final String SIGNED_URL = "https://video-bucket.s3.amazonaws.com/videos/x?sig=1";

    @Mock
    private S3Presigner s3Presigner;

    private S3UploadService service;

    @BeforeEach
    void setUp() {
        service = new S3UploadService(s3Presigner, BUCKET, 15);
    }

    private void givenPresignerReturnsUrl() throws Exception {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create(SIGNED_URL).toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .thenReturn(presigned);
    }

    private static void assertStatus(int expected, String reason, ResponseStatusException ex) {
        assertEquals(expected, ex.getStatusCode().value());
        assertEquals(reason, ex.getReason());
    }

    // ---------- caso exitoso ----------

    @Test
    @DisplayName("Genera la URL de carga y devuelve los metadatos esperados")
    void createsUploadUrlSuccessfully() throws Exception {
        givenPresignerReturnsUrl();
        Instant before = Instant.now();

        PresignedUploadResponse response = service.createUploadUrl(
                new PresignedUploadRequest("camara-entrada.mp4", "video/mp4"));

        Instant after = Instant.now();
        assertEquals(SIGNED_URL, response.uploadUrl());
        assertEquals(BUCKET, response.bucket());
        assertEquals("camara-entrada.mp4", response.originalFileName());
        assertEquals("video/mp4", response.contentType());
        assertTrue(response.objectKey().startsWith("videos/"));
        assertTrue(response.objectKey().endsWith(".mp4"));
        assertFalse(response.expiresAt().isBefore(before.plus(Duration.ofMinutes(15))));
        assertFalse(response.expiresAt().isAfter(after.plus(Duration.ofMinutes(15))));
    }

    @Test
    @DisplayName("La petición prefirmada usa bucket, key, contentType y duración correctos")
    void presignRequestCarriesExpectedParameters() throws Exception {
        givenPresignerReturnsUrl();

        PresignedUploadResponse response = service.createUploadUrl(
                new PresignedUploadRequest("a.mp4", "video/mp4"));

        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        PutObjectPresignRequest sent = captor.getValue();
        assertEquals(BUCKET, sent.putObjectRequest().bucket());
        assertEquals(response.objectKey(), sent.putObjectRequest().key());
        assertEquals("video/mp4", sent.putObjectRequest().contentType());
        assertEquals(Duration.ofMinutes(15), sent.signatureDuration());
    }

    @Test
    @DisplayName("Cada llamada genera una objectKey distinta")
    void generatesUniqueKeysPerCall() throws Exception {
        givenPresignerReturnsUrl();
        PresignedUploadRequest request = new PresignedUploadRequest("a.mp4", "video/mp4");

        String first = service.createUploadUrl(request).objectKey();
        String second = service.createUploadUrl(request).objectKey();

        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("El contentType se acepta sin distinguir mayúsculas")
    void acceptsUppercaseContentType() throws Exception {
        givenPresignerReturnsUrl();

        assertDoesNotThrow(() ->
                service.createUploadUrl(new PresignedUploadRequest("a.mp4", "VIDEO/MP4")));
    }

    // ---------- extensión del archivo ----------

    @ParameterizedTest(name = "[{0}] -> extensión [{1}]")
    @CsvSource({
            "VIDEO.MP4,.mp4",
            "clip.mov,.mov",
            "'clip.mp4 ',.mp4",
            "a.b.avi,.avi",
            "x.tar.gz,.gz",
            "sinextension,''",
            "termina.en.punto.,''",
            "raro.ext-rara,''",
            "largo.extensionmuylarga,''"
    })
    void extractsOnlySafeExtension(String fileName, String expectedExtension) throws Exception {
        givenPresignerReturnsUrl();

        String key = service.createUploadUrl(
                new PresignedUploadRequest(fileName, "video/mp4")).objectKey();

        assertTrue(key.startsWith("videos/"));
        assertTrue(key.endsWith(expectedExtension));
        // La parte restante es un UUID (36 caracteres) + extensión
        assertEquals("videos/".length() + 36 + expectedExtension.length(), key.length());
    }

    // ---------- validación de la petición ----------

    @Test
    @DisplayName("Petición nula -> 400")
    void nullRequestIsRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createUploadUrl(null));

        assertStatus(400, "La petición es obligatoria", ex);
        verifyNoInteractions(s3Presigner);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("fileName vacío o nulo -> 400")
    void blankFileNameIsRejected(String fileName) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createUploadUrl(new PresignedUploadRequest(fileName, "video/mp4")));

        assertStatus(400, "fileName es obligatorio", ex);
        verifyNoInteractions(s3Presigner);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("contentType vacío o nulo -> 400")
    void blankContentTypeIsRejected(String contentType) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createUploadUrl(new PresignedUploadRequest("a.mp4", contentType)));

        assertStatus(400, "contentType es obligatorio", ex);
        verifyNoInteractions(s3Presigner);
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/png", "application/pdf", "text/plain"})
    @DisplayName("contentType que no es video -> 400")
    void nonVideoContentTypeIsRejected(String contentType) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createUploadUrl(new PresignedUploadRequest("a.bin", contentType)));

        assertStatus(400, "El archivo debe ser un video", ex);
        verifyNoInteractions(s3Presigner);
    }

    // ---------- validación de la configuración ----------

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("Bucket sin configurar -> 500")
    void missingBucketIsRejected(String bucket) {
        S3UploadService misconfigured = new S3UploadService(s3Presigner, bucket, 15);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> misconfigured.createUploadUrl(new PresignedUploadRequest("a.mp4", "video/mp4")));

        assertStatus(500, "AWS_S3_BUCKET no está configurado", ex);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -5})
    @DisplayName("Duración de URL no válida -> 500")
    void invalidExpirationIsRejected(long minutes) {
        S3UploadService misconfigured = new S3UploadService(s3Presigner, BUCKET, minutes);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> misconfigured.createUploadUrl(new PresignedUploadRequest("a.mp4", "video/mp4")));

        assertStatus(500, "La duración de la URL prefirmada no es válida", ex);
    }

    // ---------- fallos de AWS ----------

    @Test
    @DisplayName("Error inesperado del presigner -> 500 con causa original")
    void presignerFailureProduces500() {
        RuntimeException cause = new RuntimeException("credenciales inválidas");
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenThrow(cause);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createUploadUrl(new PresignedUploadRequest("a.mp4", "video/mp4")));

        assertStatus(500, "No fue posible generar la URL de carga de S3", ex);
        assertSame(cause, ex.getCause());
    }
}
