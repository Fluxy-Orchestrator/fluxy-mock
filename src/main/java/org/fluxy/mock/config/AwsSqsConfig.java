package org.fluxy.mock.config;
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import java.net.URI;
@Configuration
public class AwsSqsConfig {
    @Value("${app.aws.region}")
    private String region;
    @Value("${app.aws.access-key}")
    private String accessKey;
    @Value("${app.aws.secret-key}")
    private String secretKey;
    @Value("${app.aws.sqs.endpoint:#{null}}")
    private String endpoint;
    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));
        var builder = SqsAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials);
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
    @Bean
    public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(SqsAsyncClient sqsAsyncClient) {
        return SqsMessageListenerContainerFactory.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options.acknowledgementMode(AcknowledgementMode.ON_SUCCESS))
                .build();
    }
}