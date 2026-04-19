package org.fluxy.mock.repository;

import org.fluxy.mock.model.MockRequestMatcher;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MockRequestMatcherRepository extends JpaRepository<MockRequestMatcher, Long> {
}

