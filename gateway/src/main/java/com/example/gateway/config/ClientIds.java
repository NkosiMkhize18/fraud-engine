package com.example.gateway.config;

import java.security.Principal;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Identifies the calling OAuth2 client, for rate limiting and access logs. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClientIds {

	/** Keycloak puts the client in "azp"; other OAuth2 servers use "client_id". Falls back to the token subject. */
	public static String of(Principal principal) {
		if (principal instanceof JwtAuthenticationToken token) {
			var jwt = token.getToken();
			for (String claim : new String[] { "azp", "client_id" }) {
				String value = jwt.getClaimAsString(claim);
				if (value != null && !value.isBlank()) {
					return value;
				}
			}
		}
		return principal.getName();
	}
}
