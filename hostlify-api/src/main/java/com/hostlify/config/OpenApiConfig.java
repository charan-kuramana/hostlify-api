package com.hostlify.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hostlifyOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hostlify Management API")
                        .description("Premium API for managing Hostels, Hostellers, Bookings, and Payments with high reliability.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Hostlify Team")
                                .email("support@hostlify.com")
                                .url("https://hostlify.com"))
                        .license(new License()
                                .name("Commercial")
                                .url("https://hostlify.com/license")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
