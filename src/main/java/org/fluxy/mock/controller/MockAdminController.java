package org.fluxy.mock.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.fluxy.mock.model.*;
import org.fluxy.mock.model.dto.*;
import org.fluxy.mock.service.MockAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/mock/admin")
@RequiredArgsConstructor
public class MockAdminController {

    private final MockAdminService adminService;

    // ── Full creation ────────────────────────────────────────────────────────

    @PostMapping("/full")
    public ResponseEntity<EndpointView> createFull(@Valid @RequestBody MockEndpointDto dto) {
        MockEndpoint created = adminService.createFull(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(created));
    }

    @PutMapping("/endpoints/{id}")
    public EndpointView updateEndpoint(@PathVariable Long id, @Valid @RequestBody MockEndpointDto dto) {
        return toView(adminService.updateFull(id, dto));
    }

    // ── Endpoints ────────────────────────────────────────────────────────────

    @GetMapping("/endpoints")
    public List<EndpointView> listEndpoints() {
        return adminService.listEndpoints().stream().map(this::toView).toList();
    }

    @GetMapping("/endpoints/{id}")
    public EndpointView getEndpoint(@PathVariable Long id) {
        return toView(adminService.getEndpoint(id));
    }

    @PatchMapping("/endpoints/{id}/toggle")
    public EndpointView toggleEndpoint(@PathVariable Long id) {
        return toView(adminService.toggleEndpoint(id));
    }

    @DeleteMapping("/endpoints/{id}")
    public ResponseEntity<Void> deleteEndpoint(@PathVariable Long id) {
        adminService.deleteEndpoint(id);
        return ResponseEntity.noContent().build();
    }

    // ── Responses ────────────────────────────────────────────────────────────

    @PostMapping("/endpoints/{id}/responses")
    public ResponseEntity<ResponseView> addResponse(@PathVariable Long id, @RequestBody MockResponseDto dto) {
        MockResponse resp = adminService.addResponse(id, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponseView(resp));
    }

    @GetMapping("/endpoints/{id}/responses")
    public List<ResponseView> listResponses(@PathVariable Long id) {
        return adminService.listResponses(id).stream().map(this::toResponseView).toList();
    }

    @PatchMapping("/endpoints/{endpointId}/responses/{responseId}/activate")
    public ResponseView activateResponse(@PathVariable Long endpointId, @PathVariable Long responseId) {
        return toResponseView(adminService.activateResponse(endpointId, responseId));
    }

    @PatchMapping("/endpoints/{endpointId}/responses/{responseId}/deactivate")
    public ResponseView deactivateResponse(@PathVariable Long endpointId, @PathVariable Long responseId) {
        return toResponseView(adminService.deactivateResponse(endpointId, responseId));
    }

    @DeleteMapping("/endpoints/{endpointId}/responses/{responseId}")
    public ResponseEntity<Void> deleteResponse(@PathVariable Long endpointId, @PathVariable Long responseId) {
        adminService.deleteResponse(responseId);
        return ResponseEntity.noContent().build();
    }

    // ── Matchers ─────────────────────────────────────────────────────────────

    @PostMapping("/responses/{responseId}/matchers")
    public ResponseEntity<MatcherView> setMatcher(@PathVariable Long responseId, @RequestBody MockRequestMatcherDto dto) {
        MockRequestMatcher m = adminService.setMatcher(responseId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toMatcherView(m));
    }

    @DeleteMapping("/matchers/{matcherId}")
    public ResponseEntity<Void> deleteMatcher(@PathVariable Long matcherId) {
        adminService.deleteMatcher(matcherId);
        return ResponseEntity.noContent().build();
    }

    // ── Post Actions ─────────────────────────────────────────────────────────

    @PostMapping("/responses/{responseId}/post-actions")
    public ResponseEntity<PostActionView> addPostAction(@PathVariable Long responseId, @RequestBody MockPostActionDto dto) {
        MockPostAction a = adminService.addPostAction(responseId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toPostActionView(a));
    }

    @DeleteMapping("/post-actions/{actionId}")
    public ResponseEntity<Void> deletePostAction(@PathVariable Long actionId) {
        adminService.deletePostAction(actionId);
        return ResponseEntity.noContent().build();
    }

    // ── View models (avoid circular JSON refs) ──────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EndpointView(Long id, String name, String httpMethod, String pathPattern,
                        String triggerType, String triggerBinding, String eventTargetType, String eventTargetDestination,
                        String mode, String targetBaseUrl, Map<String,String> responseJsonFieldOverrides,
                        boolean enabled, List<ResponseView> responses) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResponseView(Long id, String description, boolean active, int httpStatus,
                        Map<String,String> responseHeaders, String bodyTemplate,
                        long latencyMs, MatcherView requestMatcher,
                        List<PostActionView> postActions) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MatcherView(Long id, Map<String,String> matchHeaders, Map<String,String> matchQueryParams,
                       String matchBodyContains, Map<String,String> matchPathVariables) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PostActionView(Long id, long delayMs, String sqsQueueUrl, String sqsMessageTemplate) {}

    private EndpointView toView(MockEndpoint ep) {
        List<ResponseView> respViews = ep.getResponses() != null
                ? ep.getResponses().stream().map(this::toResponseView).toList()
                : null;
        return new EndpointView(ep.getId(), ep.getName(), ep.getHttpMethod().name(),
                ep.getPathPattern(),
                (ep.getTriggerType() != null ? ep.getTriggerType().name() : MockTriggerType.HTTP.name()),
                ep.getTriggerBinding(),
                ep.getEventTargetType() != null ? ep.getEventTargetType().name() : null,
                ep.getEventTargetDestination(),
                (ep.getMode() != null ? ep.getMode().name() : MockMode.STATIC_JSON.name()),
                ep.getTargetBaseUrl(), ep.getResponseJsonFieldOverrides(),
                ep.isEnabled(), respViews);
    }

    private ResponseView toResponseView(MockResponse r) {
        MatcherView mv = r.getRequestMatcher() != null ? toMatcherView(r.getRequestMatcher()) : null;
        List<PostActionView> pavs = r.getPostActions() != null
                ? r.getPostActions().stream().map(this::toPostActionView).toList()
                : null;
        return new ResponseView(r.getId(), r.getDescription(), r.isActive(), r.getHttpStatus(),
                r.getResponseHeaders(), r.getBodyTemplate(), r.getLatencyMs(), mv, pavs);
    }

    private MatcherView toMatcherView(MockRequestMatcher m) {
        return new MatcherView(m.getId(), m.getMatchHeaders(), m.getMatchQueryParams(),
                m.getMatchBodyContains(), m.getMatchPathVariables());
    }

    private PostActionView toPostActionView(MockPostAction a) {
        return new PostActionView(a.getId(), a.getDelayMs(), a.getSqsQueueUrl(), a.getSqsMessageTemplate());
    }
}
