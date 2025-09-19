package org.nobilis.nobichat.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.media.Schema;
import org.nobilis.nobichat.util.AiProperties;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;


@Configuration
@SecurityScheme(name = "tokenAuth", in = SecuritySchemeIn.HEADER, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@OpenAPIDefinition(
        info = @Info(
                title = "Nobichat API",
                version = "v1"
        ),
        servers = {
                @Server(url = "${skip-gateway-routing:/}", description = "Traefik")
        }
)
public class OpenApiConfig {

        @Bean
        public OpenApiCustomizer modelParameterCustomizer(AiProperties aiProperties) {
                return openApi -> {
                        var postOperation = openApi.getPaths().get("/api/llm").getPost();
                        if (postOperation == null || postOperation.getParameters() == null) {
                                return;
                        }

                        postOperation.getParameters().stream()
                                .filter(p -> "model".equals(p.getName()))
                                .findFirst()
                                .ifPresent(parameter -> {
                                        Schema<String> schema = (Schema<String>) parameter.getSchema();
                                        schema.setEnum(new ArrayList<>(aiProperties.getModels()));
                                });
                };
        }

}
