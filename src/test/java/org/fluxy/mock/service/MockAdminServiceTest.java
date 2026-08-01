package org.fluxy.mock.service;

import org.fluxy.mock.model.HttpMethodEnum;
import org.fluxy.mock.model.MockEventTargetType;
import org.fluxy.mock.model.MockMode;
import org.fluxy.mock.model.MockTriggerType;
import org.fluxy.mock.model.dto.MockEndpointDto;
import org.fluxy.mock.repository.MockEndpointRepository;
import org.fluxy.mock.repository.MockPostActionRepository;
import org.fluxy.mock.repository.MockRequestMatcherRepository;
import org.fluxy.mock.repository.MockResponseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MockAdminServiceTest {

    @Mock private MockEndpointRepository endpointRepo;
    @Mock private MockResponseRepository responseRepo;
    @Mock private MockRequestMatcherRepository matcherRepo;
    @Mock private MockPostActionRepository postActionRepo;

    private MockAdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new MockAdminService(endpointRepo, responseRepo, matcherRepo, postActionRepo);
    }

    @Test
    void proxyModeRequiresTargetBaseUrl() {
        MockEndpointDto dto = MockEndpointDto.builder()
                .httpMethod(HttpMethodEnum.GET)
                .mode(MockMode.PROXY)
                .pathPattern("/users/{id}")
                .build();

        assertThatThrownBy(() -> adminService.createFull(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetBaseUrl is required when mode is PROXY");

        verifyNoInteractions(endpointRepo, responseRepo, matcherRepo, postActionRepo);
    }

    @Test
    void captureModeRequiresTargetBaseUrl() {
        MockEndpointDto dto = MockEndpointDto.builder()
                .httpMethod(HttpMethodEnum.GET)
                .mode(MockMode.CAPTURE)
                .pathPattern("/users/{id}")
                .build();

        assertThatThrownBy(() -> adminService.createFull(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetBaseUrl is required when mode is PROXY, CAPTURE, or SECURE_CAPTURE");
    }

    @Test
    void secureCaptureModeRequiresTargetBaseUrl() {
        MockEndpointDto dto = MockEndpointDto.builder()
                .httpMethod(HttpMethodEnum.GET)
                .mode(MockMode.SECURE_CAPTURE)
                .pathPattern("/users/{id}")
                .build();

        assertThatThrownBy(() -> adminService.createFull(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetBaseUrl is required when mode is PROXY, CAPTURE, or SECURE_CAPTURE");
    }

    @Test
    void eventTriggerRequiresDestinationConfig() {
        MockEndpointDto dto = MockEndpointDto.builder()
                .httpMethod(HttpMethodEnum.POST)
                .triggerType(MockTriggerType.EVENT)
                .pathPattern("/events")
                .build();

        assertThatThrownBy(() -> adminService.createFull(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("triggerBinding is required when triggerType is EVENT");

        dto.setTriggerBinding("sqs:fluxy-mock-queue");

        assertThatThrownBy(() -> adminService.createFull(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventTargetType is required when triggerType is EVENT");

        dto.setEventTargetType(MockEventTargetType.SQS);

        assertThatThrownBy(() -> adminService.createFull(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventTargetDestination is required when triggerType is EVENT");
    }

    @Test
    void updateFullAppliesSameValidationRules() {
        MockEndpointDto dto = MockEndpointDto.builder()
                .httpMethod(HttpMethodEnum.GET)
                .mode(MockMode.PROXY)
                .pathPattern("/users/{id}")
                .build();

        assertThatThrownBy(() -> adminService.updateFull(1L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetBaseUrl is required when mode is PROXY");

        verifyNoInteractions(endpointRepo, responseRepo, matcherRepo, postActionRepo);
    }
}
