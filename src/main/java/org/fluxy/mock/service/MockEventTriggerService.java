package org.fluxy.mock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fluxy.mock.model.*;
import org.fluxy.mock.repository.MockEndpointRepository;
import org.fluxy.mock.repository.MockResponseRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockEventTriggerService {

    private final MockEndpointRepository endpointRepo;
    private final MockResponseRepository responseRepo;
    private final TemplateEngineService templateEngine;
    private final MockEventPublisherService eventPublisher;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public void handle(String triggerBinding, String payload) {
        List<MockEndpoint> endpoints = endpointRepo.findByTriggerTypeAndTriggerBindingAndEnabledTrue(
                MockTriggerType.EVENT, triggerBinding);

        if (endpoints.isEmpty()) {
            log.debug("No event mock registered for {}", triggerBinding);
            return;
        }

        endpoints.forEach(endpoint -> dispatch(endpoint, payload, triggerBinding));
    }

    private void dispatch(MockEndpoint endpoint, String payload, String triggerBinding) {
        RequestData reqData = new RequestData(Collections.emptyMap(), Collections.emptyMap(), payload.getBytes(StandardCharsets.UTF_8));
        MockResponse chosen = findMatchingResponse(endpoint, reqData);
        if (chosen == null) {
            log.warn("No active event mock response matched for {}", triggerBinding);
            return;
        }

        applyLatency(chosen);
        Map<String, String> ctx = templateEngine.buildContext(Collections.emptyMap(), Collections.emptyMap());
        ctx.put("event.binding", triggerBinding);
        ctx.put("event.payload", payload);
        ctx.putIfAbsent("payload", payload);

        String resolvedPayload = templateEngine.resolve(chosen.getBodyTemplate(), ctx);
        eventPublisher.publish(endpoint.getEventTargetType(), endpoint.getEventTargetDestination(), resolvedPayload, 0);
    }

    private MockResponse findMatchingResponse(MockEndpoint endpoint, RequestData reqData) {
        String bodyStr = reqData.body() != null ? new String(reqData.body(), StandardCharsets.UTF_8) : null;
        List<MockResponse> active = responseRepo.findByMockEndpointIdAndActiveTrue(endpoint.getId());

        return active.stream()
                .filter(resp -> matchesRequest(resp.getRequestMatcher(), Collections.emptyMap(),
                        Collections.emptyMap(), Collections.emptyMap(), bodyStr))
                .findFirst()
                .orElse(null);
    }

    private void applyLatency(MockResponse chosen) {
        if (chosen.getLatencyMs() <= 0) {
            return;
        }
        try {
            Thread.sleep(chosen.getLatencyMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

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

    private record RequestData(Map<String, String> queryParams, Map<String, Collection<String>> headers, byte[] body) {}
}
