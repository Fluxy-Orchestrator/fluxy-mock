package org.fluxy.mock.model.dto;

import lombok.*;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MockRequestMatcherDto {
    private Map<String, String> matchHeaders;
    private Map<String, String> matchQueryParams;
    private String matchBodyContains;
    private Map<String, String> matchPathVariables;
}

