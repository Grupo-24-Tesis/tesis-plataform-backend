package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.dto.PresignedUploadRequest;
import com.facecluster.facecluster_backend.dto.PresignedUploadResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class S3UploadService {

    private final S3Presigner s3Presigner;
    private final String bucket;
    private final long expirationMinutes;

    public S3UploadService(
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.presigned-url-duration-minutes}") long expirationMinutes
    ) {
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.expirationMinutes = expirationMinutes;
    }

    public PresignedUploadResponse createUploadUrl(
            PresignedUploadRequest request
    ) {
        validateConfiguration();
        validateRequest(request);

        String objectKey = createObjectKey(request.fileName());

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(request.contentType())
                .build();

        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(
                                Duration.ofMinutes(expirationMinutes)
                        )
                        .putObjectRequest(putObjectRequest)
                        .build();

        PresignedPutObjectRequest presignedRequest =
                s3Presigner.presignPutObject(presignRequest);

        Instant expiresAt = Instant.now()
                .plus(Duration.ofMinutes(expirationMinutes));

        return new PresignedUploadResponse(
                presignedRequest.url().toString(),
                bucket,
                objectKey,
                request.fileName(),
                request.contentType(),
                expiresAt
        );
    }

    private void validateConfiguration() {
        if (bucket == null || bucket.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AWS_S3_BUCKET no está configurado"
            );
        }

        if (expirationMinutes <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "La duración de la URL prefirmada no es válida"
            );
        }
    }

    private void validateRequest(PresignedUploadRequest request) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La petición es obligatoria"
            );
        }

        if (request.fileName() == null ||
                request.fileName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "fileName es obligatorio"
            );
        }

        if (request.contentType() == null ||
                request.contentType().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "contentType es obligatorio"
            );
        }

        String normalizedContentType =
                request.contentType().toLowerCase(Locale.ROOT);

        if (!normalizedContentType.startsWith("video/")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El archivo debe ser un video"
            );
        }
    }

    private String createObjectKey(String originalFileName) {
        String extension = extractSafeExtension(originalFileName);

        return "videos/"
                + UUID.randomUUID()
                + extension;
    }

    private String extractSafeExtension(String fileName) {
        String cleanFileName = fileName.trim();

        int lastDot = cleanFileName.lastIndexOf('.');

        if (lastDot < 0 ||
                lastDot == cleanFileName.length() - 1) {
            return "";
        }

        String extension = cleanFileName
                .substring(lastDot)
                .toLowerCase(Locale.ROOT);

        if (!extension.matches("\\.[a-z0-9]{1,10}")) {
            return "";
        }

        return extension;
    }
}