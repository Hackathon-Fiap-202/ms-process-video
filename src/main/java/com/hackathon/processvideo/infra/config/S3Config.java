package com.hackathon.processvideo.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@Profile("!dev")
public class S3Config {
    @Value("${spring.cloud.aws.credentials.access-key}")
    private String accessKeyId;

    @Value("${spring.cloud.aws.credentials.secret-key}")
    private String secretAccessKey;

    private final DefaultCredentialsProvider credentialsProvider =
            DefaultCredentialsProvider.builder().build();

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.US_EAST_1)
                .serviceConfiguration(
                        S3Configuration
                                .builder()
                                .pathStyleAccessEnabled(true)
                                .build()
                )
                .credentialsProvider(credentialsProvider)
                .build();
    }

}
