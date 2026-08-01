package org.fluxy.mock.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.fluxy.mock.model.MockCaptureSnapshot;
import org.fluxy.mock.model.MockEndpoint;
import org.fluxy.mock.repository.MockCaptureSnapshotRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MockCaptureService {

    private static final Set<String> IGNORED_HEADERS = Set.of(
            "host", "content-length", "connection", "accept-encoding",
            "transfer-encoding", "keep-alive", "proxy-authorization");

    private final MockCaptureSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;
    private final FeignProxyClient feignProxyClient;
    private final MockResponseOverrideService responseOverrideService;

    public ResponseEntity<String> resolve(MockEndpoint endpoint, String method, String subPath,
                                          Map<String, String> queryParams,
                                          Map<String, String> pathVars,
                                          Map<String, Collection<String>> headers,
                                          byte[] body) {
        return resolve(endpoint, method, subPath, queryParams, pathVars, headers, body, false);
    }

    public ResponseEntity<String> resolve(MockEndpoint endpoint, String method, String subPath,
                                          Map<String, String> queryParams,
                                          Map<String, String> pathVars,
                                          Map<String, Collection<String>> headers,
                                          byte[] body,
                                          boolean cacheOnly2xx) {
        String cacheKey = buildCacheKey(endpoint, method, subPath, queryParams, headers, body);
        Optional<MockCaptureSnapshot> cached = snapshotRepository.findByMockEndpointIdAndCacheKey(endpoint.getId(), cacheKey);
        if (cached.isPresent()) {
            return toResponseEntity(cached.get());
        }

        FeignProxyClient.ProxyResult proxyResult = feignProxyClient.forwardDetailed(
                endpoint.getTargetBaseUrl(), method, subPath, queryParams, headers, body);
        ResponseEntity<String> transformed = responseOverrideService.apply(
                proxyResult.responseEntity(), endpoint.getResponseJsonFieldOverrides(), pathVars, queryParams);
        if (shouldCache(transformed, proxyResult.cacheable(), cacheOnly2xx)) {
            snapshotRepository.save(toSnapshot(endpoint, cacheKey, transformed));
        }
        return transformed;
    }

    private boolean shouldCache(ResponseEntity<String> responseEntity, boolean cacheable, boolean cacheOnly2xx) {
        if (!cacheable) {
            return false;
        }
        if (!cacheOnly2xx) {
            return true;
        }
        int status = responseEntity.getStatusCode().value();
        return status >= 200 && status < 300;
    }

    private MockCaptureSnapshot toSnapshot(MockEndpoint endpoint, String cacheKey, ResponseEntity<String> responseEntity) {
        Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
        responseEntity.getHeaders().forEach((name, values) -> responseHeaders.put(name, new ArrayList<>(values)));

        String responseHeadersJson;
        try {
            responseHeadersJson = objectMapper.writeValueAsString(responseHeaders);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize captured response headers", e);
        }

        return MockCaptureSnapshot.builder()
                .mockEndpoint(endpoint)
                .cacheKey(cacheKey)
                .httpStatus(responseEntity.getStatusCode().value())
                .responseHeadersJson(responseHeadersJson)
                .body(responseEntity.getBody())
                .build();
    }

    private ResponseEntity<String> toResponseEntity(MockCaptureSnapshot snapshot) {
        HttpHeaders headers = new HttpHeaders();
        if (snapshot.getResponseHeadersJson() != null && !snapshot.getResponseHeadersJson().isBlank()) {
            try {
                Map<String, List<String>> restored = objectMapper.readValue(
                        snapshot.getResponseHeadersJson(), new TypeReference<Map<String, List<String>>>() {});
                restored.forEach((name, values) -> headers.put(name, new ArrayList<>(values)));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to deserialize captured response headers", e);
            }
        }

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(snapshot.getHttpStatus()).headers(headers);
        return snapshot.getBody() != null ? builder.body(snapshot.getBody()) : builder.build();
    }

    private String buildCacheKey(MockEndpoint endpoint, String method, String subPath, Map<String, String> queryParams,
                                 Map<String, Collection<String>> headers, byte[] body) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(method).append('\n').append(subPath).append('\n');
        appendSortedMap(canonical, endpoint.getResponseJsonFieldOverrides());
        canonical.append('\n');
        appendSortedMap(canonical, queryParams);
        canonical.append('\n');
        appendSortedHeaders(canonical, headers);
        canonical.append('\n');
        if (body != null && body.length > 0) {
            canonical.append(new String(body, StandardCharsets.UTF_8));
        }
        return sha256Hex(canonical.toString());
    }

    private void appendSortedMap(StringBuilder canonical, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> canonical.append(entry.getKey()).append('=').append(entry.getValue()).append(';'));
    }

    private void appendSortedHeaders(StringBuilder canonical, Map<String, Collection<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        headers.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .filter(entry -> !IGNORED_HEADERS.contains(entry.getKey().toLowerCase()))
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> {
                    canonical.append(entry.getKey().toLowerCase()).append('=');
                    List<String> values = new ArrayList<>(entry.getValue());
                    values.sort(String::compareTo);
                    canonical.append(String.join(",", values)).append(';');
                });
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
