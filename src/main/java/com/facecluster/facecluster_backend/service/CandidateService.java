package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.dto.MatchDetectionResponse;
import com.facecluster.facecluster_backend.domain.FaceEmbedding;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.FaceEmbeddingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CandidateService {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;

    private record Scored(String faceId, double score) {}

    private final FaceEmbeddingRepository embeddingRepository;
    private final FaceAppearanceRepository appearanceRepository;
    private final FaceMatchService faceMatchService;
    private final String model;
    private final int defaultLimit;
    private final double defaultMinScore;

    public CandidateService(
            FaceEmbeddingRepository embeddingRepository,
            FaceAppearanceRepository appearanceRepository,
            FaceMatchService faceMatchService,
            @Value("${matching.model}") String model,
            @Value("${matching.candidates-limit:100}") int defaultLimit,
            @Value("${matching.min-score:0.55}") double defaultMinScore
    ) {
        this.embeddingRepository = embeddingRepository;
        this.appearanceRepository = appearanceRepository;
        this.faceMatchService = faceMatchService;
        this.model = model;
        this.defaultLimit = defaultLimit;
        if (defaultMinScore < -1 || defaultMinScore > 1) {
            throw new IllegalStateException("matching.min-score debe estar entre -1 y 1");
        }
        this.defaultMinScore = defaultMinScore;
    }

    public List<MatchDetectionResponse> candidates(String faceId, Integer limit, Double minScore) {
        int effectiveLimit = limit == null ? defaultLimit : limit;

        if (effectiveLimit < MIN_LIMIT || effectiveLimit > MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit debe estar entre 1 y 100");
        }

        if (minScore != null && (minScore < -1 || minScore > 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minScore debe estar entre -1 y 1");
        }

        double cutOff = minScore == null ? defaultMinScore : minScore;

        String jobId = appearanceRepository.findById(faceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Detección no encontrada: " + faceId))
                .getJobId();

        FaceEmbedding own = embeddingRepository.findById(faceId + ":" + model)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "La detección no tiene embedding"));

        float[] reference = EmbeddingCodec.fromBytes(own.getVector());
        Set<String> decided = new java.util.HashSet<>(faceMatchService.decidedWith(faceId));
        decided.addAll(faceMatchService.confirmedGroupOf(faceId));
        List<Scored> scored = new ArrayList<>();

        for (FaceEmbedding other : embeddingRepository.findCandidateVectors(model, jobId)) {
            if (other.getVector() == null
                    || other.getJobId() == null
                    || other.getJobId().equals(jobId)
                    || faceId.equals(other.getFaceId())
                    || decided.contains(other.getFaceId())) {
                continue;
            }

            float[] vector = EmbeddingCodec.fromBytes(other.getVector());

            if (vector.length != reference.length) {
                continue;
            }

            double score = EmbeddingCodec.dot(reference, vector);

            if (score >= cutOff) {
                scored.add(new Scored(other.getFaceId(), score));
            }
        }

        scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparing(Scored::faceId));
        List<Scored> best = scored.size() > effectiveLimit ? scored.subList(0, effectiveLimit) : scored;

        if (best.isEmpty()) {
            return List.of();
        }

        Map<String, Double> scores = new LinkedHashMap<>();
        for (Scored item : best) {
            scores.put(item.faceId(), item.score());
        }

        List<MatchDetectionResponse> detections =
                new ArrayList<>(faceMatchService.detections(List.copyOf(scores.keySet()), scores));
        Map<String, Integer> rank = new HashMap<>();
        int position = 0;
        for (String id : scores.keySet()) {
            rank.put(id, position++);
        }

        detections.sort(Comparator
                .comparing((MatchDetectionResponse item) -> item.score(), Comparator.reverseOrder())
                .thenComparing(MatchDetectionResponse::capturedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(item -> rank.getOrDefault(item.faceId(), Integer.MAX_VALUE)));

        return detections;
    }
}
