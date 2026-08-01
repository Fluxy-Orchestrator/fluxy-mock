package org.fluxy.mock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fluxy.mock.model.*;
import org.fluxy.mock.repository.MockEndpointRepository;
import org.fluxy.mock.repository.MockResponseRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockResolverService {

    private final MockEndpointRepository endpointRepo;
    private final MockResponseRepository responseRepo;
    private final TemplateEngineService templateEngine;
    private final FeignProxyClient feignProxy;
    private final MockCaptureService captureService;
    private final MockResponseOverrideService responseOverrideService;
    private final PostActionExecutor postActionExecutor;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Main resolution logic: given the incoming request sub-path and all request data,
     * decide whether to return a mock response or proxy to the real service.
     */
    public ResponseEntity<String> resolve(String subPath, HttpServletRequest request) {
        String method = request.getMethod().toUpperCase();
        HttpMethodEnum httpMethod = parseMethod(method);
        if (httpMethod == null) {
            return ResponseEntity.badRequest().body("{\"error\":\"Unsupported HTTP method: " + method + "\"}");
        }

        MatchResult match = findMatchingEndpoint(httpMethod, subPath);
        RequestData reqData = extractRequestData(request);

        if (match.endpoint() == null || !match.endpoint().isEnabled()) {
            return proxyOrNotFound(match.endpoint(), method, subPath, reqData);
        }

        MockMode mode = effectiveMode(match.endpoint());
        if (mode == MockMode.PROXY) {
            ResponseEntity<String> proxied = proxyOrNotFound(match.endpoint(), method, subPath, reqData);
            return responseOverrideService.apply(proxied, match.endpoint().getResponseJsonFieldOverrides(),
                    match.pathVars(), reqData.queryParams());
        }
        if (mode == MockMode.CAPTURE || mode == MockMode.SECURE_CAPTURE) {
            return captureService.resolve(match.endpoint(), method, subPath,
                    reqData.queryParams(), match.pathVars(), reqData.headers(), reqData.body(),
                    mode == MockMode.SECURE_CAPTURE);
        }

        MockResponse chosen = findMatchingResponse(match, reqData);
        if (chosen == null) {
            return ResponseEntity.status(404)
                    .body("{\"error\":\"No active mock response matched for " + method + " " + subPath + "\"}");
        }

        applyLatency(chosen);
        // Build context once so all templates (body + post-actions) share the same generated values
        Map<String, String> ctx = templateEngine.buildContext(match.pathVars(), reqData.queryParams());
        String resolvedBody = templateEngine.resolve(chosen.getBodyTemplate(), ctx);
        HttpHeaders responseHeaders = buildResponseHeaders(chosen);
        firePostActions(chosen, ctx);

        log.info("Returning mock response (status={}) for {} {}", chosen.getHttpStatus(), method, subPath);
        return ResponseEntity.status(HttpStatusCode.valueOf(chosen.getHttpStatus()))
                .headers(responseHeaders)
                .body(resolvedBody);
    }

    // ── Resolve helpers ──────────────────────────────────────────────────────

    private HttpMethodEnum parseMethod(String method) {
        try {
            return HttpMethodEnum.valueOf(method);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private MatchResult findMatchingEndpoint(HttpMethodEnum httpMethod, String subPath) {
        for (MockEndpoint ep : endpointRepo.findByHttpMethodAndTriggerTypeAndEnabledTrue(httpMethod, MockTriggerType.HTTP)) {
            if (pathMatcher.match(ep.getPathPattern(), subPath)) {
                Map<String, String> vars = pathMatcher.extractUriTemplateVariables(ep.getPathPattern(), subPath);
                return new MatchResult(ep, vars);
            }
        }
        return new MatchResult(null, Collections.emptyMap());
    }

    private RequestData extractRequestData(HttpServletRequest request) {
        Map<String, String> queryParams = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> queryParams.put(k, v.length > 0 ? v[0] : ""));

        Map<String, Collection<String>> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, Collections.list(request.getHeaders(name)));
        }

        byte[] body;
        try {
            body = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            log.warn("Failed to read request body: {}", e.getMessage());
            body = null;
        }

        return new RequestData(queryParams, headers, body);
    }

    private MockMode effectiveMode(MockEndpoint endpoint) {
        return endpoint.getMode() != null ? endpoint.getMode() : MockMode.STATIC_JSON;
    }

    private ResponseEntity<String> proxyOrNotFound(MockEndpoint endpoint, String method, String subPath, RequestData reqData) {
        String targetBaseUrl = endpoint != null ? endpoint.getTargetBaseUrl() : null;
        if (targetBaseUrl == null) {
            return ResponseEntity.status(404)
                    .body("{\"error\":\"No mock endpoint registered for " + method + " " + subPath + "\"}");
        }
        return feignProxy.forward(targetBaseUrl, method, subPath, reqData.queryParams(), reqData.headers(), reqData.body());
    }

    private MockResponse findMatchingResponse(MatchResult match, RequestData reqData) {
        String bodyStr = reqData.body() != null ? new String(reqData.body(), StandardCharsets.UTF_8) : null;
        List<MockResponse> active = responseRepo.findByMockEndpointIdAndActiveTrue(match.endpoint().getId());

        return active.stream()
                .filter(resp -> matchesRequest(resp.getRequestMatcher(), match.pathVars(),
                        reqData.queryParams(), reqData.headers(), bodyStr))
                .findFirst()
                .orElse(null);
    }

    private void applyLatency(MockResponse chosen) {
        if (chosen.getLatencyMs() <= 0) return;
        try {
            Thread.sleep(chosen.getLatencyMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private HttpHeaders buildResponseHeaders(MockResponse chosen) {
        HttpHeaders headers = new HttpHeaders();
        if (chosen.getResponseHeaders() != null) {
            chosen.getResponseHeaders().forEach(headers::set);
        }
        if (headers.getFirst("Content-Type") == null) {
            headers.set("Content-Type", "application/json");
        }
        return headers;
    }

    private void firePostActions(MockResponse chosen, Map<String, String> ctx) {
        if (chosen.getPostActions() == null || chosen.getPostActions().isEmpty()) return;
        chosen.getPostActions().forEach(action -> postActionExecutor.execute(action, ctx));
    }

    // ── Matching logic ───────────────────────────────────────────────────────

    private boolean matchesRequest(MockRequestMatcher matcher, Map<String, String> pathVars,
                                   Map<String, String> queryParams,
                                   Map<String, Collection<String>> headers, String body) {
        if (matcher == null) return true;

        return matchesMap(matcher.getMatchPathVariables(), pathVars)
                && matchesMap(matcher.getMatchQueryParams(), queryParams)
                && matchesHeaders(matcher.getMatchHeaders(), headers)
                && matchesBodyContains(matcher.getMatchBodyContains(), body);
    }

    private boolean matchesMap(Map<String, String> expected, Map<String, String> actual) {
        if (expected == null || expected.isEmpty()) return true;
        return expected.entrySet().stream()
                .allMatch(e -> e.getValue().equals(actual.get(e.getKey())));
    }

    private boolean matchesHeaders(Map<String, String> expected, Map<String, Collection<String>> actual) {
        if (expected == null || expected.isEmpty()) return true;
        Map<String, String> flat = new HashMap<>();
        actual.forEach((k, v) -> flat.put(k.toLowerCase(), v.stream().findFirst().orElse("")));
        return expected.entrySet().stream()
                .allMatch(e -> e.getValue().equals(flat.get(e.getKey().toLowerCase())));
    }

    private boolean matchesBodyContains(String expected, String body) {
        return expected == null || expected.isBlank() || (body != null && body.contains(expected));
    }

    // ── Records ──────────────────────────────────────────────────────────────

    private record MatchResult(MockEndpoint endpoint, Map<String, String> pathVars) {}
    private record RequestData(Map<String, String> queryParams, Map<String, Collection<String>> headers, byte[] body) {}
}
