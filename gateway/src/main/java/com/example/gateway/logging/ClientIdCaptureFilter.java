package com.example.gateway.logging;

import com.example.gateway.config.ClientIds;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Records the authenticated client id as an exchange attribute so {@link AccessLogFilter}, which runs before
 * security, can log it. Registered inside the security chain between authentication and authorization (see
 * SecurityConfig), so requests rejected with 403 are attributed too.
 */
public class ClientIdCaptureFilter implements WebFilter {

	public static final String CLIENT_ID_ATTRIBUTE = ClientIdCaptureFilter.class.getName() + ".clientId";

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		return ReactiveSecurityContextHolder.getContext()
				.mapNotNull(context -> context.getAuthentication())
				.doOnNext(authentication -> exchange.getAttributes().put(CLIENT_ID_ATTRIBUTE,
						ClientIds.of(authentication)))
				.then(chain.filter(exchange));
	}
}
