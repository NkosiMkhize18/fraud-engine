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

@Configuration
public class OpenApiConfig {

	static final String SCHEME = "keycloak";

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
