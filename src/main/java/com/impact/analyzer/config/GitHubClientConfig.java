package com.impact.analyzer.config;

import java.net.http.HttpClient;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GitHubClientConfig {

    /**
     * Creates and configures a reusable HttpClient bean.
     * This separates the HttpClient setup concern from the service logic.
     */
    @Bean
    public HttpClient githubHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}