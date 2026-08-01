package org.fluxy.mock.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fluxy.mock.service.MockEventTriggerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.listener.enabled", havingValue = "true")
public class KafkaListenerService {

    private final MockEventTriggerService eventTriggerService;

    @Value("${app.kafka.topic-name}")
    private String topicName;

    @KafkaListener(topics = "${app.kafka.topic-name}")
    public void handleMessage(String message) {
        log.info("Mensaje recibido desde Kafka: {}", message);
        eventTriggerService.handle("kafka:" + topicName, message);
    }
}
