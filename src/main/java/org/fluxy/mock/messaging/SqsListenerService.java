package org.fluxy.mock.messaging;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.fluxy.mock.service.MockEventTriggerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
@Slf4j
@Service
@RequiredArgsConstructor
public class SqsListenerService {
    private final MockEventTriggerService eventTriggerService;

    @Value("${app.aws.sqs.queue-name}")
    private String queueName;

    @SqsListener("${app.aws.sqs.queue-name}")
    public void handleMessage(String message) {
        log.info("Mensaje recibido desde SQS: {}", message);
        eventTriggerService.handle("sqs:" + queueName, message);
    }
}