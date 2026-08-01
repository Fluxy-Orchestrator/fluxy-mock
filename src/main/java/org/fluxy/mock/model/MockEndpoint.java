package org.fluxy.mock.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "mock_endpoints")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MockEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HttpMethodEnum httpMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MockTriggerType triggerType = MockTriggerType.HTTP;

    @Enumerated(EnumType.STRING)
    @Column
    @Builder.Default
    private MockMode mode = MockMode.STATIC_JSON;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mock_endpoint_response_overrides", joinColumns = @JoinColumn(name = "mock_endpoint_id"))
    @MapKeyColumn(name = "json_path")
    @Column(name = "override_template")
    @Builder.Default
    private Map<String, String> responseJsonFieldOverrides = new LinkedHashMap<>();

    /** Ant-style path pattern, e.g. /users/{id}/orders/{orderId} */
    @Column(nullable = false)
    private String pathPattern;

    /**
     * Binding for event triggers, e.g. "sqs:fluxy-mock-queue" or "kafka:orders".
     * Used only when triggerType = EVENT.
     */
    @Column
    private String triggerBinding;

    @Enumerated(EnumType.STRING)
    @Column
    private MockEventTargetType eventTargetType;

    @Column
    private String eventTargetDestination;

    /** Base URL of the real service for proxy fallback, e.g. https://api.example.com */
    @Column
    private String targetBaseUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @OneToMany(mappedBy = "mockEndpoint", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MockResponse> responses = new ArrayList<>();
}
