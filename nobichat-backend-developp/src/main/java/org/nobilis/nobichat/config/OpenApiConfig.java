package org.nobilis.nobichat.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;


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
}
