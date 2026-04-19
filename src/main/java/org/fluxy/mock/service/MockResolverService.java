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
    private final PostActionExecutor postActionExecutor;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Main resolution logic: given the incoming request sub-path and all request data,
     * decide whether to return a mock response or proxy to the real service.
     */
    public ResponseEntity<String> resolve(String subPath, HttpServletRequest request) {
        String method = request.getMethod().toUpperCase();
        HttpMethodEnum httpMethod;
        try {
            httpMethod = HttpMethodEnum.valueOf(method);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("{\"error\":\"Unsupported HTTP method: " + method + "\"}");
        }

        // 1. Find matching endpoint by method + path pattern
        List<MockEndpoint> candidates = endpointRepo.findByHttpMethod(httpMethod);
        MockEndpoint matched = null;
        Map<String, String> pathVars = Collections.emptyMap();

        for (MockEndpoint ep : candidates) {
            if (pathMatcher.match(ep.getPathPattern(), subPath)) {
                matched = ep;
                pathVars = pathMatcher.extractUriTemplateVariables(ep.getPathPattern(), subPath);
                break;
            }
        }

        // Collect request data for matching & proxy
        Map<String, String> queryParams = extractQueryParams(request);
        Map<String, Collection<String>> reqHeaders = extractHeaders(request);
        byte[] body = readBody(request);
        String bodyStr = body != null ? new String(body, StandardCharsets.UTF_8) : null;

        // 2. No endpoint found or disabled → proxy
        if (matched == null || !matched.isEnabled()) {
            String targetBaseUrl = matched != null ? matched.getTargetBaseUrl() : null;
            if (targetBaseUrl == null) {
                return ResponseEntity.status(404).body("{\"error\":\"No mock endpoint registered for " + method + " " + subPath + "\"}");
            }
            return feignProxy.forward(targetBaseUrl, method, subPath, queryParams, reqHeaders, body);
        }

        // 3. Find active responses and evaluate matchers
        List<MockResponse> activeResponses = responseRepo.findByMockEndpointIdAndActiveTrue(matched.getId());
        MockResponse chosen = null;
        for (MockResponse resp : activeResponses) {
            if (matchesRequest(resp, pathVars, queryParams, reqHeaders, bodyStr)) {
                chosen = resp;
                break;
            }
        }

        // 4. No matching active response → proxy fallback
        if (chosen == null) {
            log.info("No active mock response matched for {} {} — proxying to real service", method, subPath);
            return feignProxy.forward(matched.getTargetBaseUrl(), method, subPath, queryParams, reqHeaders, body);
        }

        // 5. Apply latency
        if (chosen.getLatencyMs() > 0) {
            try {
                Thread.sleep(chosen.getLatencyMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 6. Render template
        String resolvedBody = templateEngine.resolve(chosen.getBodyTemplate(), pathVars, queryParams);

        // 7. Build response
        HttpHeaders responseHeaders = new HttpHeaders();
        if (chosen.getResponseHeaders() != null) {
            chosen.getResponseHeaders().forEach(responseHeaders::set);
        }
        if (responseHeaders.getFirst("Content-Type") == null) {
            responseHeaders.set("Content-Type", "application/json");
        }

        // 8. Fire post-actions asynchronously
        if (chosen.getPostActions() != null && !chosen.getPostActions().isEmpty()) {
            Map<String, String> finalPathVars = pathVars;
            chosen.getPostActions().forEach(action ->
                    postActionExecutor.execute(action, finalPathVars, queryParams));
        }

        log.info("Returning mock response (status={}) for {} {}", chosen.getHttpStatus(), method, subPath);
        return ResponseEntity.status(HttpStatusCode.valueOf(chosen.getHttpStatus()))
                .headers(responseHeaders)
                .body(resolvedBody);
    }

    private boolean matchesRequest(MockResponse resp, Map<String, String> pathVars,
                                    Map<String, String> queryParams,
                                    Map<String, Collection<String>> headers, String body) {
        MockRequestMatcher matcher = resp.getRequestMatcher();
        if (matcher == null) return true; // no matcher = matches everything

        // Check path variables
        if (matcher.getMatchPathVariables() != null && !matcher.getMatchPathVariables().isEmpty()) {
            for (var entry : matcher.getMatchPathVariables().entrySet()) {
                if (!entry.getValue().equals(pathVars.get(entry.getKey()))) return false;
            }
        }

        // Check query params
        if (matcher.getMatchQueryParams() != null && !matcher.getMatchQueryParams().isEmpty()) {
            for (var entry : matcher.getMatchQueryParams().entrySet()) {
                if (!entry.getValue().equals(queryParams.get(entry.getKey()))) return false;
            }
        }

        // Check headers (case-insensitive key comparison)
        if (matcher.getMatchHeaders() != null && !matcher.getMatchHeaders().isEmpty()) {
            Map<String, String> flatHeaders = new HashMap<>();
            headers.forEach((k, v) -> flatHeaders.put(k.toLowerCase(), v.stream().findFirst().orElse("")));
            for (var entry : matcher.getMatchHeaders().entrySet()) {
                String actual = flatHeaders.get(entry.getKey().toLowerCase());
                if (actual == null || !actual.equals(entry.getValue())) return false;
            }
        }

        // Check body contains
        if (matcher.getMatchBodyContains() != null && !matcher.getMatchBodyContains().isBlank()) {
            if (body == null || !body.contains(matcher.getMatchBodyContains())) return false;
        }

        return true;
    }

    private Map<String, String> extractQueryParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, v.length > 0 ? v[0] : ""));
        return params;
    }

    private Map<String, Collection<String>> extractHeaders(HttpServletRequest request) {
        Map<String, Collection<String>> headers = new HashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, Collections.list(request.getHeaders(name)));
        }
        return headers;
    }

    private byte[] readBody(HttpServletRequest request) {
        try {
            return request.getInputStream().readAllBytes();
        } catch (IOException e) {
            log.warn("Failed to read request body: {}", e.getMessage());
            return null;
        }
    }
}


