package org.fluxy.mock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fluxy.mock.config.SqsQueueInitializer;
import org.fluxy.mock.model.MockPostAction;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostActionExecutor {

    private final TemplateEngineService templateEngine;
    private final SqsAsyncClient sqsAsyncClient;
    private final SqsQueueInitializer sqsQueueInitializer;

    @Async
    public void execute(MockPostAction action, Map<String, String> ctx) {
        try {
            if (action.getDelayMs() > 0) {
                log.info("Post-action: waiting {}ms before sending SQS message", action.getDelayMs());
                Thread.sleep(action.getDelayMs());
            }

            String resolvedMessage = templateEngine.resolve(action.getSqsMessageTemplate(), ctx);

            if (action.getSqsQueueUrl() != null && !action.getSqsQueueUrl().isBlank()) {
                ensureQueueExists(action.getSqsQueueUrl());
                sqsAsyncClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(action.getSqsQueueUrl())
                        .messageBody(resolvedMessage)
                        .build()).whenComplete((resp, err) -> {
                    if (err != null) {
                        log.error("Failed to send SQS message to {}: {}", action.getSqsQueueUrl(), err.getMessage());
                    } else {
                        log.info("SQS message sent to {} — messageId={}", action.getSqsQueueUrl(), resp.messageId());
                    }
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Post-action interrupted");
        }
    }

    /**
     * Extracts the queue name from a full SQS URL and ensures the queue exists.
     * Supports both LocalStack URLs (http://localhost:4566/000000000000/my-queue)
     * and standard AWS URLs (https://sqs.us-east-1.amazonaws.com/123/my-queue).
     */
    private void ensureQueueExists(String queueUrl) {
        String queueName = queueUrl.substring(queueUrl.lastIndexOf('/') + 1);
        if (!queueName.isBlank()) {
            sqsQueueInitializer.createQueueIfAbsent(queueName);
        }
    }
}

