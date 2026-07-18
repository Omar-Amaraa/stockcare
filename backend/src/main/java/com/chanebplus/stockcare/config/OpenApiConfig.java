package com.chanebplus.stockcare.config;

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

    private static final String SCHEME = "bearer-jwt";

    @Bean
    public OpenAPI stockCareOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("StockCare API")
                        .version("0.1.0")
                        .description("MVP API for pharmacy shortage forecasting, depot prioritization and delivery tracking. "
                                + "Prediction, priority and routing responses are clearly labelled as mock/rule-based.")
                        .contact(new Contact().name("Team Chaneb+"))
                        .license(new License().name("Proprietary (academic MVP)")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME))
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
