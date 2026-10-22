package com.certisafe.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI en {@code http://localhost:8080/swagger-ui.html}.
 * Permite pegar el access token (boton Authorize) y probar los endpoints sin el frontend.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI certiSafeOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CertiSafe API")
                        .version("1.0.0")
                        .description("""
                                API REST de gestion de talleres de seguridad industrial y certificaciones.

                                Roles: ADMIN, INSTRUCTOR, OPERATOR.
                                Autenticacion: JWT (Authorization: Bearer <accessToken>).
                                Listados: paginados mediante page/size/sort.
                                """))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
