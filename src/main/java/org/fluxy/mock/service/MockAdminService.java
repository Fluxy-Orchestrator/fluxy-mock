package org.fluxy.mock.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.fluxy.mock.model.*;
import org.fluxy.mock.model.dto.*;
import org.fluxy.mock.repository.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class MockAdminService {

    private final MockEndpointRepository endpointRepo;
    private final MockResponseRepository responseRepo;
    private final MockRequestMatcherRepository matcherRepo;
    private final MockPostActionRepository postActionRepo;

    @Transactional
    public MockEndpoint createFull(MockEndpointDto dto) {
        validateEndpointConfig(dto);
        MockEndpoint endpoint = buildEndpoint(dto);
        endpoint = endpointRepo.save(endpoint);
        replaceResponses(endpoint, dto.getResponses());
        return endpointRepo.findById(endpoint.getId()).orElseThrow();
    }

    @Transactional
    public MockEndpoint updateFull(Long id, MockEndpointDto dto) {
        validateEndpointConfig(dto);
        MockEndpoint endpoint = getEndpoint(id);
        applyEndpointFields(endpoint, dto);
        endpointRepo.saveAndFlush(endpoint);
        replaceResponses(endpoint, dto.getResponses());
        return endpointRepo.findById(id).orElseThrow();
    }

    private void validateEndpointConfig(MockEndpointDto dto) {
        MockMode mode = dto.getMode() != null ? dto.getMode() : MockMode.STATIC_JSON;
        MockTriggerType triggerType = dto.getTriggerType() != null ? dto.getTriggerType() : MockTriggerType.HTTP;

        if (triggerType == MockTriggerType.HTTP && dto.getHttpMethod() == null) {
            throw new IllegalArgumentException("httpMethod is required when triggerType is HTTP");
        }

        if ((mode == MockMode.PROXY || mode == MockMode.CAPTURE || mode == MockMode.SECURE_CAPTURE)
                && (dto.getTargetBaseUrl() == null || dto.getTargetBaseUrl().isBlank())) {
            throw new IllegalArgumentException("targetBaseUrl is required when mode is PROXY, CAPTURE, or SECURE_CAPTURE");
        }

        if (triggerType == MockTriggerType.EVENT) {
            if (dto.getTriggerBinding() == null || dto.getTriggerBinding().isBlank()) {
                throw new IllegalArgumentException("triggerBinding is required when triggerType is EVENT");
            }
            if (dto.getEventTargetType() == null) {
                throw new IllegalArgumentException("eventTargetType is required when triggerType is EVENT");
            }
            if (dto.getEventTargetDestination() == null || dto.getEventTargetDestination().isBlank()) {
                throw new IllegalArgumentException("eventTargetDestination is required when triggerType is EVENT");
            }
        }
    }

    private MockEndpoint buildEndpoint(MockEndpointDto dto) {
        return MockEndpoint.builder()
                .name(dto.getName())
                .httpMethod(dto.getHttpMethod() != null ? dto.getHttpMethod() : HttpMethodEnum.POST)
                .triggerType(dto.getTriggerType() != null ? dto.getTriggerType() : MockTriggerType.HTTP)
                .mode(dto.getMode() != null ? dto.getMode() : MockMode.STATIC_JSON)
                .responseJsonFieldOverrides(dto.getResponseJsonFieldOverrides() != null
                        ? dto.getResponseJsonFieldOverrides()
                        : new LinkedHashMap<>())
                .pathPattern(dto.getPathPattern())
                .triggerBinding(dto.getTriggerBinding())
                .eventTargetType(dto.getEventTargetType())
                .eventTargetDestination(dto.getEventTargetDestination())
                .targetBaseUrl(dto.getTargetBaseUrl())
                .enabled(dto.isEnabled())
                .build();
    }

    private void applyEndpointFields(MockEndpoint endpoint, MockEndpointDto dto) {
        endpoint.setName(dto.getName());
        endpoint.setHttpMethod(dto.getHttpMethod() != null ? dto.getHttpMethod() : HttpMethodEnum.POST);
        endpoint.setTriggerType(dto.getTriggerType() != null ? dto.getTriggerType() : MockTriggerType.HTTP);
        endpoint.setMode(dto.getMode() != null ? dto.getMode() : MockMode.STATIC_JSON);
        endpoint.setResponseJsonFieldOverrides(dto.getResponseJsonFieldOverrides() != null
                ? dto.getResponseJsonFieldOverrides()
                : new LinkedHashMap<>());
        endpoint.setPathPattern(dto.getPathPattern());
        endpoint.setTriggerBinding(dto.getTriggerBinding());
        endpoint.setEventTargetType(dto.getEventTargetType());
        endpoint.setEventTargetDestination(dto.getEventTargetDestination());
        endpoint.setTargetBaseUrl(dto.getTargetBaseUrl());
        endpoint.setEnabled(dto.isEnabled());
    }

    public List<MockEndpoint> listEndpoints() {
        return endpointRepo.findAll();
    }

    public MockEndpoint getEndpoint(Long id) {
        return endpointRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Endpoint not found: " + id));
    }

    @Transactional
    public MockEndpoint toggleEndpoint(Long id) {
        MockEndpoint ep = getEndpoint(id);
        ep.setEnabled(!ep.isEnabled());
        return endpointRepo.save(ep);
    }

    @Transactional
    public void deleteEndpoint(Long id) {
        endpointRepo.deleteById(id);
    }

    @Transactional
    public MockResponse addResponse(Long endpointId, MockResponseDto dto) {
        MockEndpoint endpoint = getEndpoint(endpointId);
        return createResponse(endpoint, dto);
    }

    public List<MockResponse> listResponses(Long endpointId) {
        return responseRepo.findByMockEndpointId(endpointId);
    }

    private void replaceResponses(MockEndpoint endpoint, List<MockResponseDto> responseDtos) {
        endpoint.getResponses().clear();
        endpointRepo.saveAndFlush(endpoint);

        if (responseDtos != null) {
            for (MockResponseDto rDto : responseDtos) {
                createResponse(endpoint, rDto);
            }
        }
    }

    @Transactional
    public MockResponse activateResponse(Long endpointId, Long responseId) {
        MockResponse resp = responseRepo.findById(responseId)
                .orElseThrow(() -> new NoSuchElementException("Response not found: " + responseId));
        if (!resp.getMockEndpoint().getId().equals(endpointId)) {
            throw new IllegalArgumentException("Response does not belong to endpoint " + endpointId);
        }
        resp.setActive(true);
        return responseRepo.save(resp);
    }

    @Transactional
    public MockResponse deactivateResponse(Long endpointId, Long responseId) {
        MockResponse resp = responseRepo.findById(responseId)
                .orElseThrow(() -> new NoSuchElementException("Response not found: " + responseId));
        if (!resp.getMockEndpoint().getId().equals(endpointId)) {
            throw new IllegalArgumentException("Response does not belong to endpoint " + endpointId);
        }
        resp.setActive(false);
        return responseRepo.save(resp);
    }

    @Transactional
    public void deleteResponse(Long responseId) {
        responseRepo.deleteById(responseId);
    }

    @Transactional
    public MockRequestMatcher setMatcher(Long responseId, MockRequestMatcherDto dto) {
        MockResponse resp = responseRepo.findById(responseId)
                .orElseThrow(() -> new NoSuchElementException("Response not found: " + responseId));
        if (resp.getRequestMatcher() != null) {
            matcherRepo.delete(resp.getRequestMatcher());
            resp.setRequestMatcher(null);
            responseRepo.saveAndFlush(resp);
        }
        MockRequestMatcher matcher = MockRequestMatcher.builder()
                .mockResponse(resp)
                .matchHeaders(dto.getMatchHeaders() != null ? dto.getMatchHeaders() : new HashMap<>())
                .matchQueryParams(dto.getMatchQueryParams() != null ? dto.getMatchQueryParams() : new HashMap<>())
                .matchBodyContains(dto.getMatchBodyContains())
                .matchPathVariables(dto.getMatchPathVariables() != null ? dto.getMatchPathVariables() : new HashMap<>())
                .build();
        return matcherRepo.save(matcher);
    }

    @Transactional
    public void deleteMatcher(Long matcherId) {
        matcherRepo.deleteById(matcherId);
    }

    @Transactional
    public MockPostAction addPostAction(Long responseId, MockPostActionDto dto) {
        MockResponse resp = responseRepo.findById(responseId)
                .orElseThrow(() -> new NoSuchElementException("Response not found: " + responseId));
        MockPostAction action = MockPostAction.builder()
                .mockResponse(resp)
                .delayMs(dto.getDelayMs())
                .sqsQueueUrl(dto.getSqsQueueUrl())
                .sqsMessageTemplate(dto.getSqsMessageTemplate())
                .build();
        return postActionRepo.save(action);
    }

    @Transactional
    public void deletePostAction(Long actionId) {
        postActionRepo.deleteById(actionId);
    }

    private MockResponse createResponse(MockEndpoint endpoint, MockResponseDto rDto) {
        MockResponse resp = MockResponse.builder()
                .mockEndpoint(endpoint)
                .description(rDto.getDescription())
                .active(rDto.isActive())
                .httpStatus(rDto.getHttpStatus() > 0 ? rDto.getHttpStatus() : 200)
                .responseHeaders(rDto.getResponseHeaders() != null ? rDto.getResponseHeaders() : new HashMap<>())
                .bodyTemplate(rDto.getBodyTemplate())
                .latencyMs(rDto.getLatencyMs())
                .build();
        resp = responseRepo.save(resp);

        if (rDto.getRequestMatcher() != null) {
            MockRequestMatcherDto mDto = rDto.getRequestMatcher();
            MockRequestMatcher matcher = MockRequestMatcher.builder()
                    .mockResponse(resp)
                    .matchHeaders(mDto.getMatchHeaders() != null ? mDto.getMatchHeaders() : new HashMap<>())
                    .matchQueryParams(mDto.getMatchQueryParams() != null ? mDto.getMatchQueryParams() : new HashMap<>())
                    .matchBodyContains(mDto.getMatchBodyContains())
                    .matchPathVariables(mDto.getMatchPathVariables() != null ? mDto.getMatchPathVariables() : new HashMap<>())
                    .build();
            matcherRepo.save(matcher);
        }

        if (rDto.getPostActions() != null) {
            for (MockPostActionDto paDto : rDto.getPostActions()) {
                MockPostAction action = MockPostAction.builder()
                        .mockResponse(resp)
                        .delayMs(paDto.getDelayMs())
                        .sqsQueueUrl(paDto.getSqsQueueUrl())
                        .sqsMessageTemplate(paDto.getSqsMessageTemplate())
                        .build();
                postActionRepo.save(action);
            }
        }
        return resp;
    }
}
