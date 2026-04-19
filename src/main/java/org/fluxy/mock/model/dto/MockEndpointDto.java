package org.fluxy.mock.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.fluxy.mock.model.HttpMethodEnum;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MockEndpointDto {

    private String name;

    @NotNull(message = "httpMethod is required")
    private HttpMethodEnum httpMethod;

    @NotBlank(message = "pathPattern is required")
    private String pathPattern;

    @NotBlank(message = "targetBaseUrl is required")
    private String targetBaseUrl;

    @Builder.Default
    private boolean enabled = true;

    private List<MockResponseDto> responses;
}


