package org.fluxy.mock.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

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

    /** Ant-style path pattern, e.g. /users/{id}/orders/{orderId} */
    @Column(nullable = false)
    private String pathPattern;

    /** Base URL of the real service for proxy fallback, e.g. https://api.example.com */
    @Column(nullable = false)
    private String targetBaseUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @OneToMany(mappedBy = "mockEndpoint", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MockResponse> responses = new ArrayList<>();
}

