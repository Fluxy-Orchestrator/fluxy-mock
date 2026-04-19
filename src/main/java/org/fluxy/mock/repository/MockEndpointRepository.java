package org.fluxy.mock.repository;

import org.fluxy.mock.model.HttpMethodEnum;
import org.fluxy.mock.model.MockEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MockEndpointRepository extends JpaRepository<MockEndpoint, Long> {
    List<MockEndpoint> findByHttpMethod(HttpMethodEnum httpMethod);
}

