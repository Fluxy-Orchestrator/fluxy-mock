package org.fluxy.mock.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mock_capture_snapshots",
        uniqueConstraints = @UniqueConstraint(columnNames = {"mock_endpoint_id", "cache_key"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MockCaptureSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mock_endpoint_id", nullable = false)
    private MockEndpoint mockEndpoint;

    @Column(name = "cache_key", nullable = false)
    private String cacheKey;

    @Column(nullable = false)
    private int httpStatus;

    @Column(columnDefinition = "TEXT")
    private String responseHeadersJson;

    @Column(columnDefinition = "TEXT")
    private String body;
}
