package com.vegeai.backend.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 3 configuration.
 * Swagger UI available at: http://localhost:8080/swagger-ui.html
 * API docs JSON at:        http://localhost:8080/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI vegeaiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("VEGEAI Backend API")
                        .description("REST API for the VEGEAI Vegan/Vegetarian Support Platform. " +
                                "Use the Authorize button to provide your Bearer token for protected endpoints.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("VEGEAI Team")
                                .email("dev@vegeai.com"))
                        .license(new License().name("MIT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your access token here. Obtain it from POST /api/auth/login")));
    }
}
