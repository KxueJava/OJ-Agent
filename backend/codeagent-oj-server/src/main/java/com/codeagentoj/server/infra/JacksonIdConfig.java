package com.codeagentoj.server.infra;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** JavaScript cannot represent arbitrary 64-bit identifiers without precision loss. */
@Configuration
public class JacksonIdConfig {
    @Bean
    SimpleModule longIdSerializationModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        return module;
    }
}
