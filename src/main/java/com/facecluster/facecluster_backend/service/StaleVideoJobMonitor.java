package com.facecluster.facecluster_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class StaleVideoJobMonitor {

    private static final Logger log =
            LoggerFactory.getLogger(StaleVideoJobMonitor.class);

    private final VideoJobService videoJobService;

    public StaleVideoJobMonitor(VideoJobService videoJobService) {
        this.videoJobService = videoJobService;
    }

    @Scheduled(
            initialDelayString = "${video-jobs.stale-check-initial-delay-ms}",
            fixedDelayString = "${video-jobs.stale-check-delay-ms}"
    )
    public void failStaleJobs() {
        try {
            int failed = videoJobService.failStaleJobs(Instant.now());

            if (failed > 0) {
                log.warn(
                        "{} trabajos pasaron a FAILED por falta de respuesta del pipeline",
                        failed
                );
            }
        } catch (RuntimeException exception) {
            log.error(
                    "No se pudieron revisar los trabajos sin respuesta",
                    exception
            );
        }
    }
}
