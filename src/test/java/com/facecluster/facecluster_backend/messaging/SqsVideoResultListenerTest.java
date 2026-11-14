package com.facecluster.facecluster_backend.messaging;

import com.facecluster.facecluster_backend.service.VideoJobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqsVideoResultListenerTest {

    private static final String QUEUE_URL = "https://sqs.us-east-2.amazonaws.com/1/resultados";

    private static final String COMPLETED_BODY = """
            {
              "eventType": "VIDEO_PROCESSING_COMPLETED",
              "eventVersion": "1",
              "jobId": "job-1",
              "sentAt": "2026-11-14T12:00:00.123456Z",
              "appearances": [
                {
                  "faceId": "job-1_4",
                  "timestamp": "00:01:23",
                  "frameUrl": "https://bucket.s3.us-east-2.amazonaws.com/faces/job-1/4.jpg",
                  "capturedAt": "2026-10-16T10:05:21-05:00"
                }
              ]
            }
            """;

    private static final int MAX_RECEIVES = 5;

    @Mock
    private SqsClient sqsClient;

    @Mock
    private VideoJobService videoJobService;

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void appliesTheEventAndDeletesTheMessage() {
        SqsVideoResultListener listener = listener(QUEUE_URL);
        queueHolds(COMPLETED_BODY);

        listener.poll();

        ArgumentCaptor<VideoProcessingEvent> event =
                ArgumentCaptor.forClass(VideoProcessingEvent.class);
        verify(videoJobService).applyWorkerEvent(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(VideoProcessingEvent.COMPLETED);
        assertThat(event.getValue().jobId()).isEqualTo("job-1");
        assertThat(event.getValue().sentAt())
                .isEqualTo(Instant.parse("2026-11-14T12:00:00.123456Z"));
        assertThat(event.getValue().appearances()).hasSize(1);
        assertThat(event.getValue().appearances().get(0).faceId()).isEqualTo("job-1_4");
        verifyDeleted();
    }

    @Test
    void failedEventCarriesTheReason() {
        SqsVideoResultListener listener = listener(QUEUE_URL);
        queueHolds("""
                {"eventType": "VIDEO_PROCESSING_FAILED", "eventVersion": "1",
                 "jobId": "job-1", "sentAt": "2026-11-14T12:00:00.000001Z",
                 "reason": "video no encontrado"}
                """);

        listener.poll();

        ArgumentCaptor<VideoProcessingEvent> event =
                ArgumentCaptor.forClass(VideoProcessingEvent.class);
        verify(videoJobService).applyWorkerEvent(event.capture());
        assertThat(event.getValue().reason()).isEqualTo("video no encontrado");
        assertThat(event.getValue().appearances()).isNull();
    }

    @Test
    void malformedMessageIsDeletedWithoutReachingTheService() {
        SqsVideoResultListener listener = listener(QUEUE_URL);
        queueHolds("esto no es json");

        listener.poll();

        verifyNoInteractions(videoJobService);
        verifyDeleted();
    }

    @Test
    void invalidEventIsDeleted() {
        SqsVideoResultListener listener = listener(QUEUE_URL);
        queueHolds(COMPLETED_BODY);
        doThrow(new InvalidWorkerEventException("Job no encontrado"))
                .when(videoJobService).applyWorkerEvent(any());

        listener.poll();

        verifyDeleted();
    }

    @Test
    void messageStaysInTheQueueWhenProcessingFails() {
        SqsVideoResultListener listener = listener(QUEUE_URL);
        queueHolds(COMPLETED_BODY);
        doThrow(new IllegalStateException("mongo caido"))
                .when(videoJobService).applyWorkerEvent(any());

        listener.poll();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void messageStaysInTheQueueBelowTheReceiveCap() {
        SqsVideoResultListener listener = listener(QUEUE_URL);
        queueHolds(COMPLETED_BODY, "4");
        doThrow(new IllegalStateException("mongo caido"))
                .when(videoJobService).applyWorkerEvent(any());

        listener.poll();

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void messageIsDeletedAtTheReceiveCap() {
        SqsVideoResultListener listener = listener(QUEUE_URL);
        queueHolds(COMPLETED_BODY, "5");
        doThrow(new IllegalStateException("mongo caido"))
                .when(videoJobService).applyWorkerEvent(any());

        listener.poll();

        verifyDeleted();
    }

    @Test
    void receiveRequestAsksForTheReceiveCount() {
        SqsVideoResultListener listener = listener(QUEUE_URL);
        queueHolds(COMPLETED_BODY);

        listener.poll();

        ArgumentCaptor<ReceiveMessageRequest> request =
                ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqsClient).receiveMessage(request.capture());
        assertThat(request.getValue().messageSystemAttributeNames())
                .containsExactly(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
    }

    @Test
    void receiveErrorDoesNotPropagate() {
        SqsVideoResultListener listener = listener(QUEUE_URL);
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenThrow(new IllegalStateException("sin credenciales"));

        listener.poll();

        verifyNoInteractions(videoJobService);
    }

    @Test
    void doesNothingWithoutQueueUrl() {
        SqsVideoResultListener listener = listener("");

        listener.poll();

        verifyNoInteractions(sqsClient, videoJobService);
    }

    private SqsVideoResultListener listener(String queueUrl) {
        return new SqsVideoResultListener(
                sqsClient, objectMapper, videoJobService, queueUrl, MAX_RECEIVES
        );
    }

    private void queueHolds(String body) {
        queueHolds(body, null);
    }

    private void queueHolds(String body, String receiveCount) {
        Message.Builder builder = Message.builder()
                .messageId("m-1")
                .receiptHandle("recibo-1")
                .body(body);
        if (receiveCount != null) {
            builder.attributes(Map.of(
                    MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, receiveCount
            ));
        }
        Message message = builder.build();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
    }

    private void verifyDeleted() {
        ArgumentCaptor<DeleteMessageRequest> request =
                ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqsClient).deleteMessage(request.capture());
        assertThat(request.getValue().queueUrl()).isEqualTo(QUEUE_URL);
        assertThat(request.getValue().receiptHandle()).isEqualTo("recibo-1");
    }
}
