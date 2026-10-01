package com.codeagentoj.judge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootApplication
public class OjJudgeWorkerApplication {
    @Bean
    Queue submissionsQueue(@Value("${app.judge.queue:oj.submissions}") String name) {
        return new Queue(name, true);
    }
    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
    public static void main(String[] args) {
        SpringApplication.run(OjJudgeWorkerApplication.class, args);
    }
}
