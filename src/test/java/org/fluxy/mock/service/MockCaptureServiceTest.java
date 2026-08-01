package org.fluxy.mock.service;

import org.fluxy.mock.model.HttpMethodEnum;
import org.fluxy.mock.model.MockCaptureSnapshot;
import org.fluxy.mock.model.MockEndpoint;
import org.fluxy.mock.model.MockMode;
import org.fluxy.mock.repository.MockCaptureSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockCaptureServiceTest {

    @Mock private MockCaptureSnapshotRepository snapshotRepository;
    @Mock private FeignProxyClient feignProxyClient;
    @Mock private MockResponseOverrideService responseOverrideService;

    private MockCaptureService captureService;

    @BeforeEach
    void setUp() {
        captureService = new MockCaptureService(snapshotRepository, new com.fasterxml.jackson.databind.ObjectMapper(),
                feignProxyClient, responseOverrideService);
    }

    @Test
    void capturesFirstUpstreamResponseAndReusesIt() {
        MockEndpoint endpoint = MockEndpoint.builder()
                .id(1L)
                .name("Users")
                .httpMethod(HttpMethodEnum.GET)
                .mode(MockMode.CAPTURE)
                .pathPattern("/users/{id}")
                .targetBaseUrl("https://example.com")
                .enabled(true)
                .build();

        ResponseEntity<String> upstream = ResponseEntity.status(201)
                .header("X-Captured", "true")
                .body("{\"id\":\"42\"}");

        AtomicReference<MockCaptureSnapshot> stored = new AtomicReference<>();
        when(snapshotRepository.findByMockEndpointIdAndCacheKey(eq(1L), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(feignProxyClient.forwardDetailed(eq("https://example.com"), eq("GET"), eq("/users/42"),
                anyMap(), anyMap(), any())).thenReturn(new FeignProxyClient.ProxyResult(upstream, true));
        when(responseOverrideService.apply(eq(upstream), anyMap(), anyMap(), anyMap())).thenReturn(upstream);
        when(snapshotRepository.save(any(MockCaptureSnapshot.class))).thenAnswer(invocation -> {
            MockCaptureSnapshot snapshot = invocation.getArgument(0);
            snapshot.setId(10L);
            stored.set(snapshot);
            return snapshot;
        });

        ResponseEntity<String> first = captureService.resolve(endpoint, "GET", "/users/42",
                Map.of("page", "1"), Map.of("id", "42"), Map.of(), null);
        ResponseEntity<String> second = captureService.resolve(endpoint, "GET", "/users/42",
                Map.of("page", "1"), Map.of("id", "42"), Map.of(), null);

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(first.getHeaders().getFirst("X-Captured")).isEqualTo("true");
        assertThat(first.getBody()).isEqualTo("{\"id\":\"42\"}");
        assertThat(second.getStatusCode().value()).isEqualTo(201);
        assertThat(second.getHeaders().getFirst("X-Captured")).isEqualTo("true");
        assertThat(second.getBody()).isEqualTo("{\"id\":\"42\"}");
        verify(feignProxyClient, times(1)).forwardDetailed(eq("https://example.com"), eq("GET"), eq("/users/42"),
                anyMap(), anyMap(), any());
        verify(snapshotRepository, times(1)).save(any(MockCaptureSnapshot.class));
    }

    @Test
    void nonCacheableUpstreamResponseIsNotStored() {
        MockEndpoint endpoint = MockEndpoint.builder()
                .id(1L)
                .name("Users")
                .httpMethod(HttpMethodEnum.GET)
                .mode(MockMode.CAPTURE)
                .pathPattern("/users/{id}")
                .targetBaseUrl("https://example.com")
                .enabled(true)
                .build();

        when(snapshotRepository.findByMockEndpointIdAndCacheKey(eq(1L), anyString()))
                .thenReturn(Optional.empty());
        when(feignProxyClient.forwardDetailed(eq("https://example.com"), eq("GET"), eq("/users/42"),
                anyMap(), anyMap(), any()))
                .thenReturn(new FeignProxyClient.ProxyResult(ResponseEntity.status(502).body("bad gateway"), false));
        when(responseOverrideService.apply(any(), anyMap(), anyMap(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<String> result = captureService.resolve(endpoint, "GET", "/users/42",
                Map.of(), Map.of(), Map.of(), null);

        assertThat(result.getStatusCode().value()).isEqualTo(502);
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void secureCaptureCachesOnly2xxResponses() {
        MockEndpoint endpoint = MockEndpoint.builder()
                .id(1L)
                .name("Users")
                .httpMethod(HttpMethodEnum.GET)
                .mode(MockMode.SECURE_CAPTURE)
                .pathPattern("/users/{id}")
                .targetBaseUrl("https://example.com")
                .enabled(true)
                .build();

        when(snapshotRepository.findByMockEndpointIdAndCacheKey(eq(1L), anyString()))
                .thenReturn(Optional.empty());
        when(feignProxyClient.forwardDetailed(eq("https://example.com"), eq("GET"), eq("/users/42"),
                anyMap(), anyMap(), any()))
                .thenReturn(new FeignProxyClient.ProxyResult(ResponseEntity.status(500).body("boom"), true),
                        new FeignProxyClient.ProxyResult(ResponseEntity.ok("{\"id\":\"42\"}"), true));
        when(responseOverrideService.apply(any(), anyMap(), anyMap(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<String> first = captureService.resolve(endpoint, "GET", "/users/42",
                Map.of(), Map.of(), Map.of(), null, true);
        ResponseEntity<String> second = captureService.resolve(endpoint, "GET", "/users/42",
                Map.of(), Map.of(), Map.of(), null, true);

        assertThat(first.getStatusCode().value()).isEqualTo(500);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        verify(snapshotRepository, times(1)).save(any(MockCaptureSnapshot.class));
    }
}
