package com.impact.analyzer.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "Impact Analyzer REST API",
                version = "v1",
                description = "API for performing semantic diff and impact analysis on Java code changes."
        )
)
@Configuration
public class SwaggerConfig {
    // This class enables Springdoc and provides metadata for the Swagger UI header
}