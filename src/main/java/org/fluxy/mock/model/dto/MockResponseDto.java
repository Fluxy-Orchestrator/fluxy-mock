package org.fluxy.mock.model.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MockResponseDto {
    private String description;
    private boolean active;
    private int httpStatus;
    private Map<String, String> responseHeaders;
    private String bodyTemplate;
    private long latencyMs;
    private MockRequestMatcherDto requestMatcher;
    private List<MockPostActionDto> postActions;
}

