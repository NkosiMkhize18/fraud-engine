package com.example.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ClientIdsTest {

	private static JwtAuthenticationToken token(Map<String, Object> claims) {
		var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("service-account-uuid")
				.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).claims(c -> c.putAll(claims))
				.build();
		return new JwtAuthenticationToken(jwt);
	}

	@Test
	void keycloakClientIdComesFromAzp() {
		assertThat(ClientIds.of(token(Map.of("azp", "transaction-submitter", "client_id", "other"))))
				.isEqualTo("transaction-submitter");
	}

	@Test
	void otherServersUseClientId() {
		assertThat(ClientIds.of(token(Map.of("client_id", "reader")))).isEqualTo("reader");
	}

	@Test
	void blankClaimsFallBackToTheSubject() {
		assertThat(ClientIds.of(token(Map.of("azp", " ")))).isEqualTo("service-account-uuid");
	}

	@Test
	void nonJwtPrincipalsUseTheirName() {
		assertThat(ClientIds.of(new TestingAuthenticationToken("docs-viewer", null))).isEqualTo("docs-viewer");
	}
}
