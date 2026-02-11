package com.hackathon.process_video.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;


@Configuration
@Profile("!dev")
public class SqsConfig {

    @Value("${spring.cloud.aws.region.static:us-east-1}")
    private String region;

    private final DefaultCredentialsProvider credentialsProvider = DefaultCredentialsProvider
            .builder()
            .build();

    @Bean
    public SqsClient sqsClientEks() {
        return SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }

}
