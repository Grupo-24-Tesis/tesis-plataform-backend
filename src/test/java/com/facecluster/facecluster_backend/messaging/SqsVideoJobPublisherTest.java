package com.facecluster.facecluster_backend.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PR-01 – Pruebas unitarias de SqsVideoJobPublisher (SqsClient simulado).
 */
@ExtendWith(MockitoExtension.class)
class SqsVideoJobPublisherTest {

    private static final String QUEUE_URL = "https://sqs.us-east-2.amazonaws.com/123/cola-videos";

    @Mock
    private SqsClient sqsClient;

    private final ObjectMapper realMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private VideoProcessingMessage message() {
        return new VideoProcessingMessage(
                "VIDEO_UPLOAD_READY", "1", "job-1", "bucket-a", "videos/uuid.mp4",
                "cam-1", "video.mp4", "video/mp4", Instant.parse("2026-10-05T10:00:00Z"));
    }

    @Test
    @DisplayName("Publica el mensaje como JSON en la cola configurada y devuelve el messageId")
    void publishesJsonToConfiguredQueue() throws Exception {
        SqsVideoJobPublisher publisher = new SqsVideoJobPublisher(sqsClient, realMapper, QUEUE_URL);
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("msg-123").build());

        String messageId = publisher.publish(message());

        assertEquals("msg-123", messageId);
        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(captor.capture());
        assertEquals(QUEUE_URL, captor.getValue().queueUrl());

        JsonNode body = realMapper.readTree(captor.getValue().messageBody());
        assertEquals("VIDEO_UPLOAD_READY", body.get("eventType").asText());
        assertEquals("1", body.get("eventVersion").asText());
        assertEquals("job-1", body.get("jobId").asText());
        assertEquals("bucket-a", body.get("bucket").asText());
        assertEquals("videos/uuid.mp4", body.get("objectKey").asText());
        assertEquals("cam-1", body.get("cameraId").asText());
        assertEquals("video.mp4", body.get("originalFileName").asText());
        assertEquals("video/mp4", body.get("contentType").asText());
    }

    @Test
    @DisplayName("Si no se puede serializar a JSON lanza IllegalStateException y no envía nada")
    void serializationFailureThrowsIllegalState() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        JsonProcessingException cause = new JsonProcessingException("fallo") { };
        when(failingMapper.writeValueAsString(any())).thenThrow(cause);
        SqsVideoJobPublisher publisher = new SqsVideoJobPublisher(sqsClient, failingMapper, QUEUE_URL);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> publisher.publish(message()));

        assertEquals("No se pudo convertir el mensaje de video a JSON", ex.getMessage());
        assertSame(cause, ex.getCause());
        verifyNoInteractions(sqsClient);
    }

    @Test
    @DisplayName("Si SQS falla se propaga la misma excepción")
    void sqsFailureIsRethrown() {
        SqsVideoJobPublisher publisher = new SqsVideoJobPublisher(sqsClient, realMapper, QUEUE_URL);
        RuntimeException failure = new RuntimeException("SQS caído");
        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> publisher.publish(message()));

        assertSame(failure, thrown);
    }
}
