package org.fluxy.mock.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.fluxy.mock.model.HttpMethodEnum;
import org.fluxy.mock.model.MockEventTargetType;
import org.fluxy.mock.model.MockMode;
import org.fluxy.mock.model.MockTriggerType;
import java.util.List;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MockEndpointDto {

    private String name;

    private HttpMethodEnum httpMethod;

    @Builder.Default
    private MockTriggerType triggerType = MockTriggerType.HTTP;

    @Builder.Default
    private MockMode mode = MockMode.STATIC_JSON;

    private Map<String, String> responseJsonFieldOverrides;

    @NotBlank(message = "pathPattern is required")
    private String pathPattern;

    private String triggerBinding;

    private MockEventTargetType eventTargetType;

    private String eventTargetDestination;

    private String targetBaseUrl;

    @Builder.Default
    private boolean enabled = true;

    private List<MockResponseDto> responses;
}
