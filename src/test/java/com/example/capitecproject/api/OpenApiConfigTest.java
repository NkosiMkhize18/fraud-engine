package com.example.capitecproject.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

	@Test
	void specUsesARelativeServerAndKeycloakClientCredentials() {
		var openApi = new OpenApiConfig().fraudEngineOpenApi("http://keycloak/token");

		assertThat(openApi.getServers()).singleElement().extracting(server -> server.getUrl()).isEqualTo("/");
		var scheme = openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.SCHEME);
		assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.OAUTH2);
		var flow = scheme.getFlows().getClientCredentials();
		assertThat(flow.getTokenUrl()).isEqualTo("http://keycloak/token");
		assertThat(flow.getScopes()).containsOnlyKeys("fraud-api/submit", "fraud-api/read");
	}
}
