package org.fluxy.mock.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueNameExistsException;

/**
 * Creates the default SQS queue on startup if it does not already exist.
 * Uses the queue name from application configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqsQueueInitializer {

    private final SqsAsyncClient sqsAsyncClient;

    @Value("${app.aws.sqs.queue-name}")
    private String defaultQueueName;

    @EventListener(ApplicationReadyEvent.class)
    public void createDefaultQueue() {
        createQueueIfAbsent(defaultQueueName);
    }

    /**
     * Creates a queue by name if it does not exist.
     * SQS createQueue is idempotent: if the queue already exists with the same
     * attributes the call succeeds and returns the existing URL.
     *
     * @param queueName bare queue name (not a URL)
     */
    public void createQueueIfAbsent(String queueName) {
        sqsAsyncClient.createQueue(CreateQueueRequest.builder()
                        .queueName(queueName)
                        .build())
                .whenComplete((resp, err) -> {
                    if (err instanceof QueueNameExistsException) {
                        log.debug("SQS queue '{}' already exists — skipping creation", queueName);
                    } else if (err != null) {
                        log.error("Failed to create SQS queue '{}': {}", queueName, err.getMessage());
                    } else {
                        log.info("SQS queue '{}' ready — url: {}", queueName, resp.queueUrl());
                    }
                });
    }
}

