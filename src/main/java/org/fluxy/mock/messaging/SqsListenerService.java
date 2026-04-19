package org.fluxy.mock.messaging;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
@Slf4j
@Service
public class SqsListenerService {
    @SqsListener("${app.aws.sqs.queue-name}")
    public void handleMessage(String message) {
        log.info("Mensaje recibido desde SQS: {}", message);
    }
}