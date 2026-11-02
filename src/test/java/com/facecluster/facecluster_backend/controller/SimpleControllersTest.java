package com.facecluster.facecluster_backend.controller;

import com.facecluster.facecluster_backend.api.HealthController;
import com.facecluster.facecluster_backend.domain.Camera;
import com.facecluster.facecluster_backend.domain.Guard;
import com.facecluster.facecluster_backend.domain.MarkedPerson;
import com.facecluster.facecluster_backend.dto.PresignedUploadRequest;
import com.facecluster.facecluster_backend.dto.PresignedUploadResponse;
import com.facecluster.facecluster_backend.dto.VideoJobResponse;
import com.facecluster.facecluster_backend.dto.VideoResultRequest;
import com.facecluster.facecluster_backend.dto.VideoUploadedRequest;
import com.facecluster.facecluster_backend.repository.CameraRepository;
import com.facecluster.facecluster_backend.repository.GuardRepository;
import com.facecluster.facecluster_backend.repository.MarkedRepository;
import com.facecluster.facecluster_backend.service.S3UploadService;
import com.facecluster.facecluster_backend.service.VideoJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * PR-01 – Controladores que solo delegan en repositorios o servicios.
 * Se verifica la delegación y el valor devuelto.
 */
class SimpleControllersTest {

    @Test
    @DisplayName("GET /api/health responde status=ok")
    void healthEndpoint() {
        Map<String, String> body = new HealthController().health();

        assertEquals("ok", body.get("status"));
        assertEquals("facecluster-backend", body.get("service"));
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class CameraControllerTests {
        @Mock CameraRepository repo;
        @InjectMocks CameraController controller;

        @Test
        void listsAllCameras() {
            List<Camera> cameras = List.of(new Camera());
            when(repo.findAll()).thenReturn(cameras);

            assertSame(cameras, controller.all());
        }

        @Test
        void createsCamera() {
            Camera camera = new Camera();
            when(repo.save(camera)).thenReturn(camera);

            assertSame(camera, controller.create(camera));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class GuardControllerTests {
        @Mock GuardRepository repo;
        @InjectMocks GuardController controller;

        @Test
        void listsAllGuards() {
            List<Guard> guards = List.of(new Guard());
            when(repo.findAll()).thenReturn(guards);

            assertSame(guards, controller.all());
        }

        @Test
        void createsGuard() {
            Guard guard = new Guard();
            when(repo.save(guard)).thenReturn(guard);

            assertSame(guard, controller.create(guard));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class MarkedControllerTests {
        @Mock MarkedRepository repo;
        @InjectMocks MarkedController controller;

        @Test
        void listsAllMarkedPeople() {
            List<MarkedPerson> people = List.of(new MarkedPerson());
            when(repo.findAll()).thenReturn(people);

            assertSame(people, controller.all());
        }

        @Test
        void marksPerson() {
            MarkedPerson person = new MarkedPerson();
            when(repo.save(person)).thenReturn(person);

            assertSame(person, controller.create(person));
        }

        @Test
        void unmarksPersonById() {
            controller.delete("abc");

            verify(repo).deleteById("abc");
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class VideoControllerTests {
        @Mock S3UploadService s3UploadService;
        @Mock VideoJobService videoJobService;
        @InjectMocks VideoController controller;

        @Test
        void delegatesUploadUrlCreation() {
            PresignedUploadRequest request = new PresignedUploadRequest("a.mp4", "video/mp4");
            PresignedUploadResponse response = new PresignedUploadResponse(
                    "url", "bucket", "key", "a.mp4", "video/mp4", Instant.now());
            when(s3UploadService.createUploadUrl(request)).thenReturn(response);

            assertSame(response, controller.createUploadUrl(request));
        }

        @Test
        void delegatesVideoRegistration() {
            VideoUploadedRequest request = new VideoUploadedRequest("a.mp4", "b", "k", "c");
            VideoJobResponse response = new VideoJobResponse(
                    "job-1", "a.mp4", "b", "k", "c", "QUEUED", Instant.now());
            when(videoJobService.registerUploadedVideo(request)).thenReturn(response);

            assertSame(response, controller.registerUploadedVideo(request));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class VideoJobControllerTests {
        @Mock VideoJobService videoJobService;
        @InjectMocks VideoJobController controller;

        @Test
        void returnsJobById() {
            VideoJobResponse response = new VideoJobResponse(
                    "job-1", "a.mp4", "b", "k", "c", "QUEUED", Instant.now());
            when(videoJobService.getJobById("job-1")).thenReturn(response);

            assertSame(response, controller.getJob("job-1"));
        }

        @Test
        void forwardsModelResultsToService() {
            VideoResultRequest request = new VideoResultRequest(List.of());

            controller.receiveResults("job-1", request);

            verify(videoJobService).processResults("job-1", request);
        }
    }
}
