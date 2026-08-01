package org.fluxy.mock.service;

import jakarta.servlet.http.HttpServletRequest;
import org.fluxy.mock.model.*;
import org.fluxy.mock.repository.MockEndpointRepository;
import org.fluxy.mock.repository.MockResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockResolverServiceTest {

    @Mock private MockEndpointRepository endpointRepo;
    @Mock private MockResponseRepository responseRepo;
    @Mock private TemplateEngineService templateEngine;
    @Mock private FeignProxyClient feignProxy;
    @Mock private MockCaptureService captureService;
    @Mock private MockResponseOverrideService responseOverrideService;
    @Mock private PostActionExecutor postActionExecutor;

    private MockResolverService resolverService;

    @BeforeEach
    void setUp() {
        resolverService = new MockResolverService(endpointRepo, responseRepo, templateEngine, feignProxy, captureService, responseOverrideService, postActionExecutor);
    }

    @Test
    void staticModeRendersMockResponse() {
        MockEndpoint endpoint = MockEndpoint.builder()
                .id(1L)
                .name("Users")
                .httpMethod(HttpMethodEnum.GET)
                .mode(MockMode.STATIC_JSON)
                .pathPattern("/users/{id}")
                .enabled(true)
                .build();
        MockResponse response = MockResponse.builder()
                .id(10L)
                .mockEndpoint(endpoint)
                .active(true)
                .httpStatus(200)
                .bodyTemplate("{\"id\":\"{{path.id}}\"}")
                .build();

        when(endpointRepo.findByHttpMethodAndTriggerTypeAndEnabledTrue(HttpMethodEnum.GET, MockTriggerType.HTTP)).thenReturn(List.of(endpoint));
        when(responseRepo.findByMockEndpointIdAndActiveTrue(1L)).thenReturn(List.of(response));
        when(templateEngine.buildContext(Map.of("id", "42"), Map.of())).thenReturn(Map.of("id", "42"));
        when(templateEngine.resolve(response.getBodyTemplate(), Map.of("id", "42"))).thenReturn("{\"id\":\"42\"}");

        ResponseEntity<String> result = resolverService.resolve("/users/42", request("GET", null));

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo("{\"id\":\"42\"}");
        verify(feignProxy, never()).forward(anyString(), anyString(), anyString(), anyMap(), anyMap(), any());
    }

    @Test
    void proxyModeForwardsToTargetService() {
        MockEndpoint endpoint = MockEndpoint.builder()
                .id(1L)
                .name("Users")
                .httpMethod(HttpMethodEnum.GET)
                .mode(MockMode.PROXY)
                .pathPattern("/users/{id}")
                .targetBaseUrl("https://example.com")
                .enabled(true)
                .build();

        when(endpointRepo.findByHttpMethodAndTriggerTypeAndEnabledTrue(HttpMethodEnum.GET, MockTriggerType.HTTP)).thenReturn(List.of(endpoint));
        ResponseEntity<String> upstream = ResponseEntity.ok("{\"name\":\"original\"}");
        ResponseEntity<String> overridden = ResponseEntity.ok("{\"name\":\"patched\"}");
        when(feignProxy.forward(eq("https://example.com"), eq("GET"), eq("/users/42"), anyMap(), anyMap(), any()))
                .thenReturn(upstream);
        when(responseOverrideService.apply(eq(upstream), anyMap(), anyMap(), anyMap())).thenReturn(overridden);

        ResponseEntity<String> result = resolverService.resolve("/users/42", request("GET", null));

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo("{\"name\":\"patched\"}");
        verifyNoInteractions(responseRepo, templateEngine, postActionExecutor);
    }

    @Test
    void captureModeReturnsCachedResponseAfterFirstUpstreamCall() {
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

        when(endpointRepo.findByHttpMethodAndTriggerTypeAndEnabledTrue(HttpMethodEnum.GET, MockTriggerType.HTTP)).thenReturn(List.of(endpoint));
        doReturn(upstream, ResponseEntity.ok("{\"id\":\"42\"}"))
                .when(captureService)
                .resolve(eq(endpoint), eq("GET"), eq("/users/42"), anyMap(), anyMap(), anyMap(), any(), eq(false));

        ResponseEntity<String> first = resolverService.resolve("/users/42", request("GET", null));
        ResponseEntity<String> second = resolverService.resolve("/users/42", request("GET", null));

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(first.getHeaders().getFirst("X-Captured")).isEqualTo("true");
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getBody()).isEqualTo("{\"id\":\"42\"}");
        verifyNoInteractions(responseRepo, templateEngine, feignProxy, postActionExecutor);
    }

    @Test
    void secureCaptureModeUsesSecureCachingPolicy() {
        MockEndpoint endpoint = MockEndpoint.builder()
                .id(1L)
                .name("Users")
                .httpMethod(HttpMethodEnum.GET)
                .mode(MockMode.SECURE_CAPTURE)
                .pathPattern("/users/{id}")
                .targetBaseUrl("https://example.com")
                .enabled(true)
                .build();

        when(endpointRepo.findByHttpMethodAndTriggerTypeAndEnabledTrue(HttpMethodEnum.GET, MockTriggerType.HTTP)).thenReturn(List.of(endpoint));
        when(captureService.resolve(eq(endpoint), eq("GET"), eq("/users/42"), anyMap(), anyMap(), anyMap(), any(), eq(true)))
                .thenReturn(ResponseEntity.status(500).body("boom"));

        ResponseEntity<String> result = resolverService.resolve("/users/42", request("GET", null));

        assertThat(result.getStatusCode().value()).isEqualTo(500);
        verify(captureService).resolve(eq(endpoint), eq("GET"), eq("/users/42"), anyMap(), anyMap(), anyMap(), any(), eq(true));
    }

    @Test
    void staticModeWithoutMatchReturnsNotFound() {
        MockEndpoint endpoint = MockEndpoint.builder()
                .id(1L)
                .name("Users")
                .httpMethod(HttpMethodEnum.GET)
                .mode(MockMode.STATIC_JSON)
                .pathPattern("/users/{id}")
                .enabled(true)
                .build();

        when(endpointRepo.findByHttpMethodAndTriggerTypeAndEnabledTrue(HttpMethodEnum.GET, MockTriggerType.HTTP)).thenReturn(List.of(endpoint));
        when(responseRepo.findByMockEndpointIdAndActiveTrue(1L)).thenReturn(List.of());

        ResponseEntity<String> result = resolverService.resolve("/users/42", request("GET", null));

        assertThat(result.getStatusCode().value()).isEqualTo(404);
        assertThat(result.getBody()).contains("No active mock response matched");
        verifyNoInteractions(feignProxy, templateEngine, postActionExecutor);
    }

    private HttpServletRequest request(String method, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/fluxy/mock");
        if (body != null) {
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
        }
        return request;
    }
}
