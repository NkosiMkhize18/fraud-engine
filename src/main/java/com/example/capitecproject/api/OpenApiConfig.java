package com.example.capitecproject.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The OpenAPI spec at /v3/api-docs, rendered by the gateway's Swagger UI. */
@Configuration
public class OpenApiConfig {

	static final String SCHEME = "keycloak";

	/**
	 * "Try it out" gets a token from Keycloak with client credentials, straight from the browser, so this is the
	 * browser-facing token URL. The server is relative so calls go to wherever the docs were loaded from (the
	 * gateway), not to this service's internal address.
	 */
	@Bean
	OpenAPI fraudEngineOpenApi(@Value("${openapi.token-url}") String tokenUrl) {
		var scopes = new Scopes()
				.addString("fraud-api/submit", "Submit transactions")
				.addString("fraud-api/read", "Read assessments");
		return new OpenAPI()
				.info(new Info().title("Fraud Rule Engine API").version("v1")
						.description("Scores transactions against configurable fraud rules."))
				.addServersItem(new Server().url("/"))
				.components(new Components().addSecuritySchemes(SCHEME, new SecurityScheme()
						.type(SecurityScheme.Type.OAUTH2)
						.flows(new OAuthFlows().clientCredentials(new OAuthFlow().tokenUrl(tokenUrl).scopes(scopes)))));
	}
}
