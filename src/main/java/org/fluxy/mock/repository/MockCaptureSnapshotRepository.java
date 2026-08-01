package org.fluxy.mock.repository;

import org.fluxy.mock.model.MockCaptureSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MockCaptureSnapshotRepository extends JpaRepository<MockCaptureSnapshot, Long> {
    Optional<MockCaptureSnapshot> findByMockEndpointIdAndCacheKey(Long mockEndpointId, String cacheKey);
}
