package com.hackathon.processvideo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@ImportAutoConfiguration(exclude = {
        io.awspring.cloud.autoconfigure.sqs.SqsAutoConfiguration.class
})
class ProcessVideoApplicationTests {

    @Test
    void contextLoads() {
    }

}
