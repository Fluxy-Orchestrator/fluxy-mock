package org.fluxy.mock.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mock_post_actions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MockPostAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mock_response_id", nullable = false)
    private MockResponse mockResponse;

    /** Delay in ms after responding before executing this action */
    @Builder.Default
    private long delayMs = 0;

    /** Full SQS queue URL to send the message to */
    private String sqsQueueUrl;

    /** Message body template with {{variable}} support */
    @Column(columnDefinition = "TEXT")
    private String sqsMessageTemplate;
}

