package com.facecluster.facecluster_backend.service;

import com.facecluster.facecluster_backend.domain.Camera;
import com.facecluster.facecluster_backend.domain.FaceAppearance;
import com.facecluster.facecluster_backend.domain.FaceMatch;
import com.facecluster.facecluster_backend.domain.VideoJob;
import com.facecluster.facecluster_backend.dto.DetectionMatchesResponse;
import com.facecluster.facecluster_backend.dto.DetectionMatchesResponse.MatchLink;
import com.facecluster.facecluster_backend.dto.FaceMatchRequest;
import com.facecluster.facecluster_backend.dto.MatchDetectionResponse;
import com.facecluster.facecluster_backend.repository.CameraRepository;
import com.facecluster.facecluster_backend.repository.FaceAppearanceRepository;
import com.facecluster.facecluster_backend.repository.FaceMatchRepository;
import com.facecluster.facecluster_backend.repository.GuardRepository;
import com.facecluster.facecluster_backend.repository.VideoJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FaceMatchService {

    public static final String CONFIRMED = "confirmada";
    public static final String DISCARDED = "descartada";

    public record DecisionResult(FaceMatch match, boolean created) {}

    private final FaceMatchRepository matchRepository;
    private final FaceAppearanceRepository appearanceRepository;
    private final VideoJobRepository videoJobRepository;
    private final CameraRepository cameraRepository;
    private final GuardRepository guardRepository;
    private final FaceImageService faceImageService;
    private final Clock clock;
    private final String model;

    public FaceMatchService(
            FaceMatchRepository matchRepository,
            FaceAppearanceRepository appearanceRepository,
            VideoJobRepository videoJobRepository,
            CameraRepository cameraRepository,
            GuardRepository guardRepository,
            FaceImageService faceImageService,
            Clock clock,
            @Value("${matching.model}") String model
    ) {
        this.matchRepository = matchRepository;
        this.appearanceRepository = appearanceRepository;
        this.videoJobRepository = videoJobRepository;
        this.cameraRepository = cameraRepository;
        this.guardRepository = guardRepository;
        this.faceImageService = faceImageService;
        this.clock = clock;
        this.model = model;
    }

    public static String pairId(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    public DecisionResult decide(FaceMatchRequest request) {
        String a = request.faceIdA();
        String b = request.faceIdB();

        if (a == null || a.isBlank() || b == null || b.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "faceIdA y faceIdB son obligatorios");
        }

        if (a.equals(b)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Una detección no puede coincidir consigo misma");
        }

        if (!CONFIRMED.equals(request.decision()) && !DISCARDED.equals(request.decision())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Decisión inválida: use confirmada o descartada");
        }

        if (request.createdByGuardId() != null
                && !guardRepository.existsById(request.createdByGuardId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El vigilante no existe");
        }

        FaceAppearance first = requireAppearance(a);
        FaceAppearance second = requireAppearance(b);

        if (first.getJobId() != null && first.getJobId().equals(second.getJobId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las dos detecciones son del mismo video");
        }

        String id = pairId(a, b);
        LocalDateTime now = LocalDateTime.now(clock);
        FaceMatch match = matchRepository.findById(id).orElse(null);
        boolean created = match == null;

        if (created) {
            match = new FaceMatch();
            match.setId(id);
            match.setFaceIdA(a.compareTo(b) <= 0 ? a : b);
            match.setFaceIdB(a.compareTo(b) <= 0 ? b : a);
            match.setCreatedAt(now);
        }

        match.setDecision(request.decision());
        match.setScore(request.score());
        match.setModel(model);
        match.setCreatedByGuardId(request.createdByGuardId());
        match.setUpdatedAt(now);

        return new DecisionResult(matchRepository.save(match), created);
    }

    public void undo(String id) {
        if (!matchRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Decisión no encontrada");
        }

        matchRepository.deleteById(id);
    }

    public DetectionMatchesResponse group(String faceId) {
        requireAppearance(faceId);

        Map<String, FaceMatch> links = reachableLinks(List.of(faceId));
        Set<String> members = component(faceId, adjacency(links));
        members.remove(faceId);

        List<MatchLink> matchLinks = links.values().stream()
                .filter(link -> inGroup(link, faceId, members))
                .sorted(Comparator.comparing(FaceMatch::getId))
                .map(link -> new MatchLink(
                        link.getId(), link.getFaceIdA(), link.getFaceIdB(),
                        link.getCreatedByGuardId(), link.getCreatedAt()))
                .toList();

        return new DetectionMatchesResponse(faceId, matchLinks, detectionsOf(members));
    }

    public Map<String, Integer> matchCounts(Collection<String> faceIds) {
        Map<String, Integer> counts = new LinkedHashMap<>();

        if (faceIds.isEmpty()) {
            return counts;
        }

        Map<String, Set<String>> adjacency = adjacency(reachableLinks(faceIds));

        for (String faceId : faceIds) {
            counts.put(faceId, component(faceId, adjacency).size() - 1);
        }

        return counts;
    }

    public List<FaceAppearance> withMatchCounts(List<FaceAppearance> appearances) {
        Set<String> ids = new LinkedHashSet<>();

        for (FaceAppearance appearance : appearances) {
            if (appearance.getFaceId() != null) {
                ids.add(appearance.getFaceId());
            }
        }

        Map<String, Integer> counts = matchCounts(ids);

        for (FaceAppearance appearance : appearances) {
            appearance.setMatchCount(counts.getOrDefault(appearance.getFaceId(), 0));
        }

        return appearances;
    }

    public boolean hasConfirmedMatch(Collection<String> faceIds) {
        if (faceIds.isEmpty()) {
            return false;
        }

        return !matchRepository.findConfirmedTouching(faceIds).isEmpty();
    }

    public Set<String> decidedWith(String faceId) {
        Set<String> others = new LinkedHashSet<>();

        for (FaceMatch match : matchRepository.findTouching(faceId)) {
            others.add(faceId.equals(match.getFaceIdA()) ? match.getFaceIdB() : match.getFaceIdA());
        }

        return others;
    }

    private FaceAppearance requireAppearance(String faceId) {
        return appearanceRepository.findById(faceId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Detección no encontrada: " + faceId));
    }

    private Map<String, FaceMatch> reachableLinks(Collection<String> start) {
        Map<String, FaceMatch> links = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>(start);
        Set<String> frontier = new LinkedHashSet<>(start);

        while (!frontier.isEmpty()) {
            Set<String> next = new LinkedHashSet<>();

            for (FaceMatch link : matchRepository.findConfirmedTouching(frontier)) {
                links.put(link.getId(), link);

                for (String end : List.of(link.getFaceIdA(), link.getFaceIdB())) {
                    if (seen.add(end)) {
                        next.add(end);
                    }
                }
            }

            frontier = next;
        }

        return links;
    }

    private Map<String, Set<String>> adjacency(Map<String, FaceMatch> links) {
        Map<String, Set<String>> adjacency = new HashMap<>();

        for (FaceMatch link : links.values()) {
            adjacency.computeIfAbsent(link.getFaceIdA(), key -> new HashSet<>()).add(link.getFaceIdB());
            adjacency.computeIfAbsent(link.getFaceIdB(), key -> new HashSet<>()).add(link.getFaceIdA());
        }

        return adjacency;
    }

    private Set<String> component(String start, Map<String, Set<String>> adjacency) {
        Set<String> visited = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            String current = queue.poll();

            for (String neighbour : adjacency.getOrDefault(current, Set.of())) {
                if (visited.add(neighbour)) {
                    queue.add(neighbour);
                }
            }
        }

        return visited;
    }

    private boolean inGroup(FaceMatch link, String start, Set<String> members) {
        return start.equals(link.getFaceIdA()) || start.equals(link.getFaceIdB())
                || members.contains(link.getFaceIdA()) || members.contains(link.getFaceIdB());
    }

    private List<MatchDetectionResponse> detectionsOf(Collection<String> faceIds) {
        if (faceIds.isEmpty()) {
            return List.of();
        }

        List<FaceAppearance> found = new ArrayList<>();
        appearanceRepository.findAllById(faceIds).forEach(found::add);
        List<FaceAppearance> withImages = faceImageService.withS3ImagesByJob(found);

        Map<String, String> cameraNames = new HashMap<>();
        for (Camera camera : cameraRepository.findAllById(
                withImages.stream().map(FaceAppearance::getCameraId).filter(id -> id != null).distinct().toList())) {
            cameraNames.put(camera.getId(), camera.getName());
        }

        Map<String, String> videoNames = new HashMap<>();
        for (VideoJob job : videoJobRepository.findAllById(
                withImages.stream().map(FaceAppearance::getJobId).filter(id -> id != null).distinct().toList())) {
            videoNames.put(job.getId(), job.getOriginalFileName());
        }

        return withImages.stream()
                .sorted(Comparator
                        .comparing(FaceAppearance::getCapturedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(FaceAppearance::getFaceId))
                .map(appearance -> new MatchDetectionResponse(
                        appearance.getFaceId(),
                        appearance.getJobId(),
                        appearance.getCameraId(),
                        cameraNames.get(appearance.getCameraId()),
                        videoNames.get(appearance.getJobId()),
                        appearance.getTimestamp(),
                        appearance.getCapturedAt(),
                        null,
                        appearance.getFrameUrl()))
                .toList();
    }
}
