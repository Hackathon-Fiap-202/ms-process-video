package com.hackathon.processvideo.infra.config;

import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import io.awspring.cloud.s3.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@Profile("dev")
public class S3ConfigDev {

    @Value("${spring.cloud.aws.s3.endpoint:default}")
    private String awsUrl;

    @Value("${spring.cloud.aws.credentials.access-key:default}")
    private String accessKeyId;

    @Value("${spring.cloud.aws.credentials.secret-key:default}")
    private String secretAccessKey;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(awsUrl))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                        )
                )
                .build();
    }

    @Bean
    public S3OutputStreamProvider s3OutputStreamProvider(S3Client s3Client) {
        return new InMemoryBufferingS3OutputStreamProvider(s3Client, new PropertiesS3ObjectContentTypeResolver());
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(awsUrl))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                        )
                )
                .build();
    }

    @Bean
    public S3Template s3Template(S3Client s3Client,
                                 S3OutputStreamProvider s3OutputStreamProvider,
                                 S3ObjectConverter s3ObjectConverter,
                                 S3Presigner s3Presigner) {
        return new S3Template(s3Client, s3OutputStreamProvider, new Jackson2JsonS3ObjectConverter(new JsonMapper()), s3Presigner);
    }
}
