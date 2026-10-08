package com.example.gateway.config;

import com.example.gateway.logging.ClientIdCaptureFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/**
 * Edge authorization: rejects calls without a valid token or the right scope before they reach the service
 * (which checks again). Only the public API and the API docs are reachable; everything else, including the
 * service's actuator endpoints, is denied.
 */
@Configuration
public class SecurityConfig {

	public static final String SUBMIT_SCOPE = "SCOPE_fraud-api/submit";
	public static final String READ_SCOPE = "SCOPE_fraud-api/read";
	public static final String METRICS_SCOPE = "SCOPE_fraud-api/metrics";

	/** Swagger UI, the spec it loads, and the OAuth2 login endpoints that protect them. */
	static final String[] DOCS_PATHS = { "/swagger-ui.html", "/swagger-ui/**", "/webjars/**", "/v3/api-docs/**",
			"/oauth2/authorization/**", "/login/oauth2/code/**", "/login", "/logout" };

	/**
	 * API docs are for people in a browser, which can't attach a Bearer token, so they use a Keycloak login and a
	 * session instead. This chain only matches the docs paths; the API below stays stateless and token-only.
	 */
	@Bean
	@Order(1)
	SecurityWebFilterChain docsSecurityWebFilterChain(ServerHttpSecurity http) {
		return http
				.securityMatcher(ServerWebExchangeMatchers.pathMatchers(DOCS_PATHS))
				.authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
				// Back to the page that triggered the login, or to the Swagger UI (not "/", which is the API).
				.oauth2Login(login -> login
						.authenticationSuccessHandler(new RedirectServerAuthenticationSuccessHandler("/swagger-ui.html")))
				.addFilterAfter(new ClientIdCaptureFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
				.build();
	}

	@Bean
	@Order(2)
	SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
		return http
				.authorizeExchange(exchanges -> exchanges
						.pathMatchers("/actuator/health/**").permitAll()
						.pathMatchers(HttpMethod.GET, "/actuator/prometheus").hasAuthority(METRICS_SCOPE)
						.pathMatchers(HttpMethod.POST, "/api/v1/transactions").hasAuthority(SUBMIT_SCOPE)
						.pathMatchers(HttpMethod.GET, "/api/v1/assessments", "/api/v1/assessments/*")
						.hasAuthority(READ_SCOPE)
						.anyExchange().denyAll())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
				.addFilterAfter(new ClientIdCaptureFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
				.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
				// Stateless bearer-token API: no cookies or sessions for CSRF to exploit.
				.csrf(csrf -> csrf.disable())
				.build();
	}
}
