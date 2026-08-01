package org.fluxy.mock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fluxy.mock.model.MockPostAction;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostActionExecutor {

    private final TemplateEngineService templateEngine;
    private final MockEventPublisherService eventPublisher;

    @Async
    public void execute(MockPostAction action, Map<String, String> ctx) {
        try {
            String resolvedMessage = templateEngine.resolve(action.getSqsMessageTemplate(), ctx);
            if (action.getSqsQueueUrl() != null && !action.getSqsQueueUrl().isBlank()) {
                eventPublisher.publish(org.fluxy.mock.model.MockEventTargetType.SQS,
                        action.getSqsQueueUrl(), resolvedMessage, action.getDelayMs());
            }
        } catch (RuntimeException e) {
            log.error("Failed to execute post-action: {}", e.getMessage());
            throw e;
        }
    }
}
