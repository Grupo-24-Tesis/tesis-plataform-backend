package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.domain.VideoJob;
import com.facecluster.facecluster_backend.dto.VideoJobResponse;
import com.facecluster.facecluster_backend.dto.VideoResultRequest;
import com.facecluster.facecluster_backend.dto.VideoResultRequest.FaceAppearanceResult;
import com.facecluster.facecluster_backend.dto.VideoUploadedRequest;
import com.facecluster.facecluster_backend.messaging.SqsVideoJobPublisher;
import com.facecluster.facecluster_backend.messaging.VideoProcessingMessage;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.VideoJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PR-01 – Pruebas unitarias de VideoJobService.
 * Repositorios y publicador SQS simulados: sin BD ni AWS reales.
 */
@ExtendWith(MockitoExtension.class)
class VideoJobServiceTest {

    @Mock
    private VideoJobRepository videoJobRepository;

    @Mock
    private FaceAppearanceRepository faceAppearanceRepository;

    @Mock
    private SqsVideoJobPublisher sqsVideoJobPublisher;

    @Captor
    private ArgumentCaptor<List<FaceAppearance>> appearancesCaptor;

    @Captor
    private ArgumentCaptor<VideoProcessingMessage> messageCaptor;

    private VideoJobService service;

    /** Estados que tenía el job en cada llamada a save(). */
    private List<String> savedStatuses;

    @BeforeEach
    void setUp() {
        service = new VideoJobService(videoJobRepository, faceAppearanceRepository, sqsVideoJobPublisher);
        savedStatuses = new ArrayList<>();
    }

    // ---------- helpers ----------

    /** save() asigna un id la primera vez y registra el estado en cada llamada. */
    private void givenSaveAssignsIdAndRecordsStatus() {
        when(videoJobRepository.save(any(VideoJob.class))).thenAnswer(invocation -> {
            VideoJob job = invocation.getArgument(0);
            if (job.getId() == null) {
                job.setId("job-1");
            }
            savedStatuses.add(job.getStatus());
            return job;
        });
    }

    private VideoUploadedRequest validUpload() {
        return new VideoUploadedRequest("video.mp4", "bucket-a", "videos/uuid.mp4", "cam-1");
    }

    private VideoJob existingJob() {
        VideoJob job = new VideoJob();
        job.setId("job-1");
        job.setOriginalFileName("video.mp4");
        job.setBucket("bucket-a");
        job.setObjectKey("videos/uuid.mp4");
        job.setCameraId("cam-from-job");
        job.setStatus("QUEUED");
        job.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));
        job.setUpdatedAt(Instant.parse("2020-01-01T00:00:00Z"));
        return job;
    }

    private static ResponseStatusException assertStatus(int status, String reason, Runnable action) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(status, ex.getStatusCode().value());
        assertEquals(reason, ex.getReason());
        return ex;
    }

    // ======================================================
    // registerUploadedVideo
    // ======================================================

    @Test
    @DisplayName("Registra el video, lo publica en SQS y queda en estado QUEUED")
    void registersVideoAndQueuesIt() {
        givenSaveAssignsIdAndRecordsStatus();
        when(sqsVideoJobPublisher.publish(any(VideoProcessingMessage.class))).thenReturn("msg-1");

        VideoJobResponse response = service.registerUploadedVideo(validUpload());

        assertEquals("job-1", response.jobId());
        assertEquals("video.mp4", response.originalFileName());
        assertEquals("bucket-a", response.bucket());
        assertEquals("videos/uuid.mp4", response.objectKey());
        assertEquals("cam-1", response.cameraId());
        assertEquals("QUEUED", response.status());
        assertNotNull(response.createdAt());
        assertEquals(List.of("UPLOADED", "QUEUED"), savedStatuses);
    }

    @Test
    @DisplayName("Guarda primero y publica después (orden correcto)")
    void savesBeforePublishing() {
        givenSaveAssignsIdAndRecordsStatus();
        when(sqsVideoJobPublisher.publish(any(VideoProcessingMessage.class))).thenReturn("msg-1");

        service.registerUploadedVideo(validUpload());

        InOrder order = inOrder(videoJobRepository, sqsVideoJobPublisher);
        order.verify(videoJobRepository).save(any(VideoJob.class));
        order.verify(sqsVideoJobPublisher).publish(any(VideoProcessingMessage.class));
        order.verify(videoJobRepository).save(any(VideoJob.class));
    }

    @Test
    @DisplayName("Elimina espacios sobrantes de los campos de texto")
    void trimsRequestFields() {
        givenSaveAssignsIdAndRecordsStatus();
        when(sqsVideoJobPublisher.publish(any(VideoProcessingMessage.class))).thenReturn("msg-1");

        VideoJobResponse response = service.registerUploadedVideo(
                new VideoUploadedRequest("  video.mp4 ", " bucket-a ", " videos/uuid.mp4 ", " cam-1 "));

        assertEquals("video.mp4", response.originalFileName());
        assertEquals("bucket-a", response.bucket());
        assertEquals("videos/uuid.mp4", response.objectKey());
        assertEquals("cam-1", response.cameraId());
    }

    @Test
    @DisplayName("El mensaje SQS lleva el evento, la versión y los datos del job")
    void publishedMessageCarriesJobData() {
        givenSaveAssignsIdAndRecordsStatus();
        when(sqsVideoJobPublisher.publish(any(VideoProcessingMessage.class))).thenReturn("msg-1");

        service.registerUploadedVideo(validUpload());

        verify(sqsVideoJobPublisher).publish(messageCaptor.capture());
        VideoProcessingMessage message = messageCaptor.getValue();
        assertEquals("VIDEO_UPLOAD_READY", message.eventType());
        assertEquals("1", message.eventVersion());
        assertEquals("job-1", message.jobId());
        assertEquals("bucket-a", message.bucket());
        assertEquals("videos/uuid.mp4", message.objectKey());
        assertEquals("cam-1", message.cameraId());
        assertEquals("video.mp4", message.originalFileName());
        assertEquals("video/mp4", message.contentType());
        assertNotNull(message.createdAt());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "video.mp4,video/mp4",
            "VIDEO.MP4,video/mp4",
            "toma.mov,video/quicktime",
            "TOMA.MOV,video/quicktime",
            "camara.avi,video/x-msvideo",
            "grabacion.mkv,video/x-matroska",
            "clip.webm,application/octet-stream",
            "sinextension,application/octet-stream"
    })
    void detectsContentTypeFromFileName(String fileName, String expectedContentType) {
        givenSaveAssignsIdAndRecordsStatus();
        when(sqsVideoJobPublisher.publish(any(VideoProcessingMessage.class))).thenReturn("msg-1");

        service.registerUploadedVideo(new VideoUploadedRequest(fileName, "b", "k", "c"));

        verify(sqsVideoJobPublisher).publish(messageCaptor.capture());
        assertEquals(expectedContentType, messageCaptor.getValue().contentType());
    }

    @Test
    @DisplayName("Si SQS falla, el job queda en QUEUE_ERROR y responde 503")
    void queueFailureMarksJobAndReturns503() {
        givenSaveAssignsIdAndRecordsStatus();
        RuntimeException cause = new RuntimeException("SQS no disponible");
        when(sqsVideoJobPublisher.publish(any(VideoProcessingMessage.class))).thenThrow(cause);

        ResponseStatusException ex = assertStatus(503,
                "El video se guardó, pero no pudo enviarse a la cola SQS",
                () -> service.registerUploadedVideo(validUpload()));

        assertSame(cause, ex.getCause());
        assertEquals(List.of("UPLOADED", "QUEUE_ERROR"), savedStatuses);
    }

    static Stream<Arguments> invalidUploadRequests() {
        return Stream.of(
                Arguments.of(null, "La petición es obligatoria"),
                Arguments.of(new VideoUploadedRequest(null, "b", "k", "c"), "originalFileName es obligatorio"),
                Arguments.of(new VideoUploadedRequest("  ", "b", "k", "c"), "originalFileName es obligatorio"),
                Arguments.of(new VideoUploadedRequest("a.mp4", null, "k", "c"), "bucket es obligatorio"),
                Arguments.of(new VideoUploadedRequest("a.mp4", "", "k", "c"), "bucket es obligatorio"),
                Arguments.of(new VideoUploadedRequest("a.mp4", "b", null, "c"), "objectKey es obligatorio"),
                Arguments.of(new VideoUploadedRequest("a.mp4", "b", " ", "c"), "objectKey es obligatorio"),
                Arguments.of(new VideoUploadedRequest("a.mp4", "b", "k", null), "cameraId es obligatorio"),
                Arguments.of(new VideoUploadedRequest("a.mp4", "b", "k", ""), "cameraId es obligatorio")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidUploadRequests")
    @DisplayName("Una petición inválida responde 400 y no toca BD ni SQS")
    void invalidUploadRequestIsRejected(VideoUploadedRequest request, String expectedReason) {
        assertStatus(400, expectedReason, () -> service.registerUploadedVideo(request));

        verifyNoInteractions(videoJobRepository, sqsVideoJobPublisher);
    }

    // ======================================================
    // getJobById
    // ======================================================

    @Test
    @DisplayName("Devuelve el job existente mapeado a VideoJobResponse")
    void returnsExistingJob() {
        VideoJob job = existingJob();
        when(videoJobRepository.findById("job-1")).thenReturn(Optional.of(job));

        VideoJobResponse response = service.getJobById("job-1");

        assertEquals("job-1", response.jobId());
        assertEquals("video.mp4", response.originalFileName());
        assertEquals("bucket-a", response.bucket());
        assertEquals("videos/uuid.mp4", response.objectKey());
        assertEquals("cam-from-job", response.cameraId());
        assertEquals("QUEUED", response.status());
        assertEquals(job.getCreatedAt(), response.createdAt());
    }

    @Test
    @DisplayName("Job inexistente -> 404")
    void unknownJobReturns404() {
        when(videoJobRepository.findById("nope")).thenReturn(Optional.empty());

        assertStatus(404, "Job no encontrado", () -> service.getJobById("nope"));
    }

    // ======================================================
    // processResults
    // ======================================================

    @Test
    @DisplayName("Guarda las apariciones con el cameraId del job y marca el job COMPLETED")
    void processResultsStoresAppearancesAndCompletesJob() {
        VideoJob job = existingJob();
        when(videoJobRepository.findById("job-1")).thenReturn(Optional.of(job));
        VideoResultRequest request = new VideoResultRequest(List.of(
                new FaceAppearanceResult("face-1", "00:00:04", "https://b.s3.amazonaws.com/faces/1.jpg",
                        "2026-10-05T14:30:00-05:00"),
                new FaceAppearanceResult("face-2", "00:00:09", "https://b.s3.amazonaws.com/faces/2.jpg", null)));

        service.processResults("job-1", request);

        verify(faceAppearanceRepository).saveAll(appearancesCaptor.capture());
        List<FaceAppearance> saved = appearancesCaptor.getValue();
        assertEquals(2, saved.size());

        FaceAppearance first = saved.get(0);
        assertEquals("face-1", first.getFaceId());
        assertEquals("00:00:04", first.getTimestamp());
        assertEquals("https://b.s3.amazonaws.com/faces/1.jpg", first.getFrameUrl());
        assertEquals("cam-from-job", first.getCameraId());
        // Se conserva la hora local del offset recibido
        assertEquals(LocalDateTime.of(2026, 10, 5, 14, 30), first.getCapturedAt());

        assertNull(saved.get(1).getCapturedAt());
        assertEquals("cam-from-job", saved.get(1).getCameraId());

        ArgumentCaptor<VideoJob> jobCaptor = ArgumentCaptor.forClass(VideoJob.class);
        verify(videoJobRepository).save(jobCaptor.capture());
        assertEquals("COMPLETED", jobCaptor.getValue().getStatus());
        assertTrue(jobCaptor.getValue().getUpdatedAt().isAfter(Instant.parse("2020-01-01T00:00:00Z")));
    }

    @Test
    @DisplayName("capturedAt vacío se guarda como null")
    void blankCapturedAtBecomesNull() {
        when(videoJobRepository.findById("job-1")).thenReturn(Optional.of(existingJob()));

        service.processResults("job-1", new VideoResultRequest(List.of(
                new FaceAppearanceResult("face-1", "t", "u", "  "))));

        verify(faceAppearanceRepository).saveAll(appearancesCaptor.capture());
        assertNull(appearancesCaptor.getValue().get(0).getCapturedAt());
    }

    @Test
    @DisplayName("Lista de apariciones vacía: completa el job sin error")
    void emptyAppearancesStillCompletesJob() {
        VideoJob job = existingJob();
        when(videoJobRepository.findById("job-1")).thenReturn(Optional.of(job));

        service.processResults("job-1", new VideoResultRequest(List.of()));

        verify(faceAppearanceRepository).saveAll(List.of());
        assertEquals("COMPLETED", job.getStatus());
    }

    @Test
    @DisplayName("processResults con job inexistente -> 404")
    void processResultsUnknownJobReturns404() {
        when(videoJobRepository.findById("nope")).thenReturn(Optional.empty());

        assertStatus(404, "Job no encontrado",
                () -> service.processResults("nope", new VideoResultRequest(List.of())));

        verifyNoInteractions(faceAppearanceRepository);
    }

    @Test
    @DisplayName("Petición nula -> 400")
    void processResultsNullRequestReturns400() {
        when(videoJobRepository.findById("job-1")).thenReturn(Optional.of(existingJob()));

        assertStatus(400, "La lista de apariciones es obligatoria",
                () -> service.processResults("job-1", null));

        verifyNoInteractions(faceAppearanceRepository);
    }

    @Test
    @DisplayName("Lista de apariciones nula -> 400")
    void processResultsNullAppearancesReturns400() {
        when(videoJobRepository.findById("job-1")).thenReturn(Optional.of(existingJob()));

        assertStatus(400, "La lista de apariciones es obligatoria",
                () -> service.processResults("job-1", new VideoResultRequest(null)));

        verifyNoInteractions(faceAppearanceRepository);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL", "''", "'   '"}, nullValues = "NULL")
    @DisplayName("faceId nulo o vacío -> 400 y no se guarda nada")
    void processResultsBlankFaceIdReturns400(String faceId) {
        when(videoJobRepository.findById("job-1")).thenReturn(Optional.of(existingJob()));
        VideoResultRequest request = new VideoResultRequest(List.of(
                new FaceAppearanceResult(faceId, "t", "u", null)));

        assertStatus(400, "faceId es obligatorio", () -> service.processResults("job-1", request));

        verify(faceAppearanceRepository, never()).saveAll(any());
        verify(videoJobRepository, never()).save(any());
    }

    /**
     * Documenta el comportamiento actual: un capturedAt con formato inválido
     * lanza DateTimeParseException (sin traducir a 400).
     * Si se corrige el servicio para responder 400, actualizar esta prueba.
     */
    @Test
    @DisplayName("capturedAt con formato inválido lanza DateTimeParseException (comportamiento actual)")
    void invalidCapturedAtThrowsParseException() {
        when(videoJobRepository.findById("job-1")).thenReturn(Optional.of(existingJob()));
        VideoResultRequest request = new VideoResultRequest(List.of(
                new FaceAppearanceResult("face-1", "t", "u", "05/10/2026")));

        assertThrows(DateTimeParseException.class, () -> service.processResults("job-1", request));

        verify(faceAppearanceRepository, never()).saveAll(any());
    }
}
