package org.fluxy.mock.service;

import org.fluxy.mock.model.*;
import org.fluxy.mock.repository.MockEndpointRepository;
import org.fluxy.mock.repository.MockResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockEventTriggerServiceTest {

    @Mock private MockEndpointRepository endpointRepo;
    @Mock private MockResponseRepository responseRepo;
    @Mock private TemplateEngineService templateEngine;
    @Mock private MockEventPublisherService eventPublisher;

    private MockEventTriggerService triggerService;

    @BeforeEach
    void setUp() {
        triggerService = new MockEventTriggerService(endpointRepo, responseRepo, templateEngine, eventPublisher);
    }

    @Test
    void publishesResolvedPayloadToConfiguredTarget() {
        MockEndpoint endpoint = MockEndpoint.builder()
                .id(1L)
                .name("Orders")
                .httpMethod(HttpMethodEnum.POST)
                .triggerType(MockTriggerType.EVENT)
                .triggerBinding("sqs:orders")
                .eventTargetType(MockEventTargetType.KAFKA)
                .eventTargetDestination("orders-events")
                .enabled(true)
                .build();

        MockResponse response = MockResponse.builder()
                .id(10L)
                .mockEndpoint(endpoint)
                .active(true)
                .bodyTemplate("{\"event\":\"ORDER_CREATED\",\"payload\":\"{{event.payload}}\"}")
                .build();

        when(endpointRepo.findByTriggerTypeAndTriggerBindingAndEnabledTrue(MockTriggerType.EVENT, "sqs:orders"))
                .thenReturn(List.of(endpoint));
        when(responseRepo.findByMockEndpointIdAndActiveTrue(1L)).thenReturn(List.of(response));
        when(templateEngine.buildContext(eq(java.util.Collections.emptyMap()), eq(java.util.Collections.emptyMap())))
                .thenReturn(new java.util.HashMap<>());
        when(templateEngine.resolve(eq(response.getBodyTemplate()), anyMap())).thenReturn("{\"event\":\"ORDER_CREATED\"}");

        triggerService.handle("sqs:orders", "{\"id\":42}");

        verify(eventPublisher).publish(MockEventTargetType.KAFKA, "orders-events", "{\"event\":\"ORDER_CREATED\"}", 0);
    }
}
