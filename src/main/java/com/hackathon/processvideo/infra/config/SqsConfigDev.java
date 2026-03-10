package com.hackathon.processvideo.infra.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@Profile("dev")
public class SqsConfigDev {
    @Value("${spring.cloud.aws.region.static:us-east-1}")
    private String region;

    @Value("${spring.cloud.aws.credentials.access-key:default}")
    private String accessKeyId;

    @Value("${spring.cloud.aws.credentials.secret-key:default}")
    private String secretAccessKey;

    @Bean
    public SqsClient sqsClientDev(
            @Value("${spring.cloud.aws.sqs.endpoint}") String endpoint
    ) {
        return SqsClient.builder()
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                        )
                )
                .build();
    }

}
