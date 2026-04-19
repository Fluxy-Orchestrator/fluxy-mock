package org.fluxy.mock.model.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MockPostActionDto {
    private long delayMs;
    private String sqsQueueUrl;
    private String sqsMessageTemplate;
}

