package com.example.capitecproject.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	public static final String SUBMIT_SCOPE = "SCOPE_fraud-api/submit";
	public static final String READ_SCOPE = "SCOPE_fraud-api/read";
	public static final String METRICS_SCOPE = "SCOPE_fraud-api/metrics";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/actuator/prometheus").hasAuthority(METRICS_SCOPE)
						.requestMatchers(HttpMethod.POST, "/api/v1/transactions").hasAuthority(SUBMIT_SCOPE)
						.requestMatchers(HttpMethod.GET, "/api/v1/assessments", "/api/v1/assessments/*")
						.hasAuthority(READ_SCOPE)
						// The OpenAPI spec: any valid token from the realm (the gateway relays the docs user's).
						.requestMatchers(HttpMethod.GET, "/v3/api-docs").authenticated()
						.anyRequest().denyAll())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// No cookies or sessions, so there is nothing for CSRF to exploit.
				.csrf(csrf -> csrf.disable())
				.build();
	}
}
