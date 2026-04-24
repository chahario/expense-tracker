package com.fenmo.expense.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures OpenAPI documentation available at /swagger-ui.html.
 *
 * Also registers the Idempotency-Key as an optional header on POST operations
 * so it shows up in the Swagger UI without annotating every controller method.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Expense Tracker API")
                        .version("1.0")
                        .description("""
                                Personal expense tracker API.
                                
                                **Idempotency:** POST /expenses accepts an optional `Idempotency-Key` header (UUID).
                                Sending the same key twice returns the original response without a duplicate insert,
                                making it safe to retry on network failures.
                                """)
                        .contact(new Contact()
                                .name("Dinesh Chahar")
                                .email("dinesh@example.com")));
    }

    /**
     * Adds the Idempotency-Key header to all POST operations in Swagger UI.
     */
    @Bean
    public OperationCustomizer idempotencyKeyCustomizer() {
        return (operation, handlerMethod) -> {
            if (handlerMethod.getMethod().isAnnotationPresent(
                    org.springframework.web.bind.annotation.PostMapping.class)) {
                operation.addParametersItem(
                        new HeaderParameter()
                                .name("Idempotency-Key")
                                .description("Client-generated UUID. Stable across retries. " +
                                             "Omit if idempotency is not needed.")
                                .required(false)
                                .example("550e8400-e29b-41d4-a716-446655440000")
                );
            }
            return operation;
        };
    }
}
