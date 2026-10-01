package com.codeagentoj.server.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/** Keeps published workspace problem data readable while execution endpoints remain authenticated. */
@Configuration
public class WorkspaceReadSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain workspaceProblemReadChain(HttpSecurity http, CorsConfigurationSource cors) throws Exception {
        http.securityMatcher("/api/workspace/problems/**")
                .csrf(csrf -> csrf.disable())
                .cors(configuration -> configuration.configurationSource(cors))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }
}
