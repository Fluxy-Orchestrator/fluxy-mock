package org.fluxy.mock.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "mock_responses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MockResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mock_endpoint_id", nullable = false)
    private MockEndpoint mockEndpoint;

    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = false;

    /** HTTP status code to return, e.g. 200, 404, 500 */
    @Column(nullable = false)
    @Builder.Default
    private int httpStatus = 200;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mock_response_headers", joinColumns = @JoinColumn(name = "mock_response_id"))
    @MapKeyColumn(name = "header_name")
    @Column(name = "header_value")
    @Builder.Default
    private Map<String, String> responseHeaders = new HashMap<>();

    /** Body template supporting {{variable}} placeholders */
    @Column(columnDefinition = "TEXT")
    private String bodyTemplate;

    /** Simulated latency in milliseconds before responding */
    @Builder.Default
    private long latencyMs = 0;

    @OneToOne(mappedBy = "mockResponse", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private MockRequestMatcher requestMatcher;

    @OneToMany(mappedBy = "mockResponse", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<MockPostAction> postActions = new ArrayList<>();
}

