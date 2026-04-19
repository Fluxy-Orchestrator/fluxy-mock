package org.fluxy.mock.repository;

import org.fluxy.mock.model.MockResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MockResponseRepository extends JpaRepository<MockResponse, Long> {
    List<MockResponse> findByMockEndpointIdAndActiveTrue(Long endpointId);
    List<MockResponse> findByMockEndpointId(Long endpointId);
}

