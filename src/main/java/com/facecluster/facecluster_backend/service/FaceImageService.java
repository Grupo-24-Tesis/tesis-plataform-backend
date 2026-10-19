package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.FaceAppearance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * S3 es la única fuente de las imágenes de rostros.
 *
 * Mongo guarda la URL permanente del objeto (la escribe el modelo). Antes de
 * responder al frontend, se descartan las apariciones cuya imagen ya no existe
 * en el bucket (o que no viene de S3) y el resto recibe una URL prefirmada de
 * corta duración, así el bucket puede ser privado y el navegador no reutiliza
 * imágenes borradas.
 */
@Service
public class FaceImageService {

    private static final Logger log =
            LoggerFactory.getLogger(FaceImageService.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String facesPrefix;
    private final Duration urlDuration;

    public FaceImageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.faces-prefix}") String facesPrefix,
            @Value("${aws.s3.presigned-url-duration-minutes}") long expirationMinutes
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.facesPrefix = facesPrefix;
        this.urlDuration = Duration.ofMinutes(expirationMinutes);
    }

    /**
     * Devuelve copias de las apariciones cuya imagen existe en S3, con la
     * frameUrl reemplazada por una URL prefirmada. Las entidades originales no
     * se modifican.
     */
    public List<FaceAppearance> withS3Images(List<FaceAppearance> appearances) {
        Set<String> existingKeys = listFaceKeys();
        List<FaceAppearance> result = new ArrayList<>();

        for (FaceAppearance appearance : appearances) {
            String key = toObjectKey(appearance.getFrameUrl());

            if (key == null || !existingKeys.contains(key)) {
                continue;
            }

            result.add(copyWithUrl(appearance, presign(key)));
        }

        return result;
    }

    private Set<String> listFaceKeys() {
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(facesPrefix)
                    .build();

            Set<String> keys = new HashSet<>();
            s3Client.listObjectsV2Paginator(request)
                    .contents()
                    .stream()
                    .map(S3Object::key)
                    .forEach(keys::add);
            return keys;

        } catch (RuntimeException exception) {
            log.error(
                    "No se pudieron listar las imágenes en S3. bucket={}, prefix={}",
                    bucket,
                    facesPrefix,
                    exception
            );

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No fue posible consultar las imágenes en S3",
                    exception
            );
        }
    }

    /**
     * Extrae la key de una URL virtual-hosted de este bucket, por ejemplo
     * https://bucket.s3.us-east-2.amazonaws.com/faces/job/4.jpg. Devuelve null
     * para cualquier otra URL.
     */
    private String toObjectKey(String frameUrl) {
        if (frameUrl == null || frameUrl.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(frameUrl.trim());
            String host = uri.getHost();

            if (host == null ||
                    !host.startsWith(bucket + ".s3") ||
                    !host.endsWith(".amazonaws.com")) {
                return null;
            }

            String path = uri.getRawPath();
            if (path == null || path.length() <= 1) {
                return null;
            }

            return URLDecoder.decode(
                    path.substring(1),
                    StandardCharsets.UTF_8
            );

        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String presign(String key) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(urlDuration)
                .getObjectRequest(builder -> builder.bucket(bucket).key(key))
                .build();

        return s3Presigner.presignGetObject(request).url().toString();
    }

    private FaceAppearance copyWithUrl(FaceAppearance source, String frameUrl) {
        FaceAppearance copy = new FaceAppearance();
        copy.setId(source.getId());
        copy.setFaceId(source.getFaceId());
        copy.setTimestamp(source.getTimestamp());
        copy.setCameraId(source.getCameraId());
        copy.setCapturedAt(source.getCapturedAt());
        copy.setFrameUrl(frameUrl);
        return copy;
    }
}
