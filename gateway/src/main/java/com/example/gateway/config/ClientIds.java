package com.example.gateway.config;

import java.security.Principal;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClientIds {

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
