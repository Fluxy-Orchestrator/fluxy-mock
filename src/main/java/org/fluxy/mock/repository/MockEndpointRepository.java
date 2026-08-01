package org.fluxy.mock.repository;

import org.fluxy.mock.model.HttpMethodEnum;
import org.fluxy.mock.model.MockEndpoint;
import org.fluxy.mock.model.MockTriggerType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MockEndpointRepository extends JpaRepository<MockEndpoint, Long> {
    @EntityGraph(attributePaths = {"responses", "responses.requestMatcher", "responses.postActions"})
    List<MockEndpoint> findAll();

    @EntityGraph(attributePaths = {"responses", "responses.requestMatcher", "responses.postActions"})
    Optional<MockEndpoint> findById(Long id);

    List<MockEndpoint> findByHttpMethodAndTriggerTypeAndEnabledTrue(HttpMethodEnum httpMethod, MockTriggerType triggerType);
    List<MockEndpoint> findByTriggerTypeAndTriggerBindingAndEnabledTrue(MockTriggerType triggerType, String triggerBinding);
}
