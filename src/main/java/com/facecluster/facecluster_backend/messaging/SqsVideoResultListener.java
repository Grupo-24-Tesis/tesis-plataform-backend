package com.facecluster.facecluster_backend.messaging;

import com.facecluster.facecluster_backend.service.VideoJobService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

@Component
public class SqsVideoResultListener {

    private static final Logger log =
            LoggerFactory.getLogger(SqsVideoResultListener.class);

    private static final int WAIT_SECONDS = 10;
    private static final int MAX_MESSAGES = 10;

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final VideoJobService videoJobService;
    private final String queueUrl;
    private final int maxReceives;

    public SqsVideoResultListener(
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            VideoJobService videoJobService,
            @Value("${aws.sqs.results-queue-url}") String queueUrl,
            @Value("${video-jobs.results-max-receives}") int maxReceives
    ) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.videoJobService = videoJobService;
        this.queueUrl = queueUrl == null ? "" : queueUrl.trim();
        this.maxReceives = maxReceives;

        if (this.queueUrl.isEmpty()) {
            log.warn(
                    "AWS_SQS_RESULTS_QUEUE_URL no está configurada: "
                            + "no se recibirán resultados del pipeline"
            );
        }
    }

    @Scheduled(fixedDelayString = "${video-jobs.results-poll-delay-ms}")
    public void poll() {
        if (queueUrl.isEmpty()) {
            return;
        }

        List<Message> messages;

        try {
            messages = sqsClient.receiveMessage(
                    ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(MAX_MESSAGES)
                            .waitTimeSeconds(WAIT_SECONDS)
                            .messageSystemAttributeNames(
                                    MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT
                            )
                            .build()
            ).messages();
        } catch (RuntimeException exception) {
            log.error(
                    "No se pudo consultar la cola de resultados. queueUrl={}",
                    queueUrl,
                    exception
            );
            return;
        }

        for (Message message : messages) {
            handle(message);
        }
    }

    private void handle(Message message) {
        VideoProcessingEvent event;

        try {
            event = objectMapper.readValue(
                    message.body(),
                    VideoProcessingEvent.class
            );
        } catch (JsonProcessingException exception) {
            log.error(
                    "Se descarta el mensaje {}: no es un evento válido",
                    message.messageId(),
                    exception
            );
            delete(message);
            return;
        }

        try {
            videoJobService.applyWorkerEvent(event);
        } catch (InvalidWorkerEventException exception) {
            log.error(
                    "Se descarta el evento {} del job {}: {}",
                    event.eventType(),
                    event.jobId(),
                    exception.getMessage()
            );
            delete(message);
            return;
        } catch (RuntimeException exception) {
            handleUnexpectedFailure(message, event, exception);
            return;
        }

        log.info(
                "Evento {} aplicado al job {}",
                event.eventType(),
                event.jobId()
        );
        delete(message);
    }

    private void handleUnexpectedFailure(
            Message message,
            VideoProcessingEvent event,
            RuntimeException exception
    ) {
        int receiveCount = receiveCount(message);

        if (receiveCount >= maxReceives) {
            log.error(
                    "Se descarta el evento {} del job {} tras {} intentos",
                    event.eventType(),
                    event.jobId(),
                    receiveCount,
                    exception
            );
            delete(message);
            return;
        }

        log.error(
                "No se pudo aplicar el evento {} del job {}; se reintentará",
                event.eventType(),
                event.jobId(),
                exception
        );
    }

    private int receiveCount(Message message) {
        String value = message.attributes()
                .get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);

        if (value == null) {
            return 0;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void delete(Message message) {
        try {
            sqsClient.deleteMessage(
                    DeleteMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .receiptHandle(message.receiptHandle())
                            .build()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "No se pudo borrar el mensaje {} de la cola de resultados",
                    message.messageId(),
                    exception
            );
        }
    }
}
