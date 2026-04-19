package org.fluxy.mock.repository;

import org.fluxy.mock.model.MockPostAction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MockPostActionRepository extends JpaRepository<MockPostAction, Long> {
    List<MockPostAction> findByMockResponseId(Long responseId);
}

