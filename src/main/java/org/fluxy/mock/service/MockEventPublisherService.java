package org.fluxy.mock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fluxy.mock.config.SqsQueueInitializer;
import org.fluxy.mock.model.MockEventTargetType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MockEventPublisherService {

    private final SqsAsyncClient sqsAsyncClient;
    private final SqsQueueInitializer sqsQueueInitializer;
    private final Optional<KafkaTemplate<String, String>> kafkaTemplate;

    @Async
    public void publish(MockEventTargetType targetType, String destination, String payload, long delayMs) {
        try {
            if (delayMs > 0) {
                log.info("Event dispatch: waiting {}ms before sending {} message", delayMs, targetType);
                Thread.sleep(delayMs);
            }

            if (targetType == MockEventTargetType.SQS) {
                publishToSqs(destination, payload);
                return;
            }

            if (targetType == MockEventTargetType.KAFKA) {
                publishToKafka(destination, payload);
                return;
            }

            throw new IllegalArgumentException("Unsupported event target type: " + targetType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Event dispatch interrupted");
        }
    }

    private void publishToSqs(String destination, String payload) {
        ensureQueueExists(destination);
        sqsAsyncClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(destination)
                        .messageBody(payload)
                        .build())
                .whenComplete((resp, err) -> {
                    if (err != null) {
                        log.error("Failed to send SQS message to {}: {}", destination, err.getMessage());
                    } else {
                        log.info("SQS message sent to {} — messageId={}", destination, resp.messageId());
                    }
                });
    }

    private void publishToKafka(String destination, String payload) {
        KafkaTemplate<String, String> template = kafkaTemplate.orElseThrow(() ->
                new IllegalStateException("KafkaTemplate is not available but targetType=KAFKA was requested"));

        template.send(destination, payload).whenComplete((resp, err) -> {
            if (err != null) {
                log.error("Failed to send Kafka message to {}: {}", destination, err.getMessage());
            } else {
                log.info("Kafka message sent to {} — offset={}", destination, resp.getRecordMetadata().offset());
            }
        });
    }

    /**
     * Extracts the queue name from a full SQS URL and ensures the queue exists.
     */
    private void ensureQueueExists(String queueUrl) {
        String queueName = queueUrl.substring(queueUrl.lastIndexOf('/') + 1);
        if (!queueName.isBlank()) {
            sqsQueueInitializer.createQueueIfAbsent(queueName);
        }
    }
}
