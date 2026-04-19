package org.fluxy.mock.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "mock_request_matchers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MockRequestMatcher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mock_response_id", nullable = false)
    private MockResponse mockResponse;

    /** Headers the incoming request must contain (subset match) */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "matcher_headers", joinColumns = @JoinColumn(name = "matcher_id"))
    @MapKeyColumn(name = "header_name")
    @Column(name = "header_value")
    @Builder.Default
    private Map<String, String> matchHeaders = new HashMap<>();

    /** Query params the incoming request must contain */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "matcher_query_params", joinColumns = @JoinColumn(name = "matcher_id"))
    @MapKeyColumn(name = "param_name")
    @Column(name = "param_value")
    @Builder.Default
    private Map<String, String> matchQueryParams = new HashMap<>();

    /** Substring that must be present in the request body */
    @Column(columnDefinition = "TEXT")
    private String matchBodyContains;

    /** Path variables that must match exactly */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "matcher_path_variables", joinColumns = @JoinColumn(name = "matcher_id"))
    @MapKeyColumn(name = "var_name")
    @Column(name = "var_value")
    @Builder.Default
    private Map<String, String> matchPathVariables = new HashMap<>();
}

