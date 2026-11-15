package com.claims.platform.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * OpenAPI 3 document shared by every service that has springdoc on its classpath (Swagger UI at
 * {@code /swagger-ui.html}, document at {@code /v3/api-docs}): title from {@code platform.openapi.*}, bearer-JWT
 * security on every operation, and one definition of the problem+json error body. Controllers only state
 * {@code @ApiResponse(responseCode, description)} for an error; its content is filled in here.
 */
@AutoConfiguration
@ConditionalOnClass({OpenAPI.class, OpenApiCustomizer.class})
public class PlatformOpenApiAutoConfiguration {
    static final String BEARER_JWT = "bearer-jwt";
    static final String PROBLEM = "Problem";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Bean
    @ConditionalOnMissingBean
    OpenAPI platformOpenApi(@Value("${platform.openapi.title:${spring.application.name:API}}") String title,
                            @Value("${platform.openapi.description:}") String description,
                            @Value("${platform.openapi.version:v1}") String version) {
        return new OpenAPI()
                .info(new Info().title(title).description(description).version(version))
                .components(new Components()
                        .addSecuritySchemes(BEARER_JWT, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("Access token from POST /auth/login"))
                        .addSchemas(PROBLEM, problemSchema()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_JWT));
    }

    /** Every 4xx/5xx response is RFC 9457 problem+json with the platform's {@code code} and {@code correlationId}. */
    @Bean
    OpenApiCustomizer problemJsonErrorResponses() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                    operation.getResponses().forEach((status, response) -> {
                        if (status.startsWith("4") || status.startsWith("5")) {
                            response.setContent(new Content().addMediaType(PROBLEM_JSON,
                                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM))));
                        }
                    })));
        };
    }

    private static Schema<?> problemSchema() {
        return new ObjectSchema()
                .description("RFC 9457 problem details")
                .addProperty("type", new StringSchema().example("https://errors.claims.example/claim-not-found"))
                .addProperty("title", new StringSchema().example("Claim not found"))
                .addProperty("status", new IntegerSchema().example(404))
                .addProperty("detail", new StringSchema().example("Claim 4f0c... not found"))
                .addProperty("code", new StringSchema()
                        .description("Stable machine-readable error code; clients switch on this").example("CLAIM_NOT_FOUND"))
                .addProperty("correlationId", new StringSchema().description("Same id as in logs and events"))
                .addProperty("errors", new ArraySchema()
                        .description("Field-level violations, for VALIDATION_FAILED")
                        .items(new ObjectSchema()
                                .addProperty("field", new StringSchema().example("incident.date"))
                                .addProperty("message", new StringSchema().example("must not be null"))));
    }
}
