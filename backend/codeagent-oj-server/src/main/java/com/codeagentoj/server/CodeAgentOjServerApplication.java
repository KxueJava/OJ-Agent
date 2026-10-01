package com.codeagentoj.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.codeagentoj.server")
public class CodeAgentOjServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodeAgentOjServerApplication.class, args);
    }
}
