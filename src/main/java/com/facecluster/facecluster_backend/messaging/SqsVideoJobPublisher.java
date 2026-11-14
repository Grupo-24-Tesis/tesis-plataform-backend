package com.facecluster.facecluster_backend.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@Service
public class SqsVideoJobPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(SqsVideoJobPublisher.class);

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String queueUrl;

    public SqsVideoJobPublisher(
            SqsClient sqsClient,
            ObjectMapper objectMapper,
            @Value("${aws.sqs.queue-url}") String queueUrl
    ) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
    }

    public String publish(
            VideoProcessingMessage message
    ) {
        try {
            String messageBody =
                    objectMapper.writeValueAsString(message);

            log.info(
                    "Publicando job {} en SQS. queueUrl={}",
                    message.jobId(),
                    queueUrl
            );

            SendMessageRequest request =
                    SendMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .messageBody(messageBody)
                            .build();

            SendMessageResponse response =
                    sqsClient.sendMessage(request);

            log.info(
                    "Mensaje enviado correctamente a SQS. jobId={}, messageId={}",
                    message.jobId(),
                    response.messageId()
            );

            return response.messageId();

        } catch (JsonProcessingException exception) {
            log.error(
                    "No se pudo convertir el job {} a JSON",
                    message.jobId(),
                    exception
            );

            throw new IllegalStateException(
                    "No se pudo convertir el mensaje de video a JSON",
                    exception
            );

        } catch (RuntimeException exception) {
            log.error(
                    "Error enviando el job {} a SQS. queueUrl={}",
                    message.jobId(),
                    queueUrl,
                    exception
            );

            throw exception;
        }
    }
}