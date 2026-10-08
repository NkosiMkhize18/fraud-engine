package com.example.gateway.logging;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * One access-log line per request, including ones rejected by security or the rate limiter. Also assigns an
 * X-Request-Id (or keeps the caller's), forwards it to the service and returns it, so a request can be traced
 * across both logs.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessLogFilter implements WebFilter {

	public static final String REQUEST_ID_HEADER = "X-Request-Id";

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		long start = System.nanoTime();
		String incoming = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
		String requestId = isValid(incoming) ? incoming : UUID.randomUUID().toString();

		ServerWebExchange traced = exchange.mutate()
				.request(request -> request.headers(headers -> headers.set(REQUEST_ID_HEADER, requestId)))
				.build();
		traced.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);

		// Log when the response is committed, not when the filter chain finishes: errors (upstream down, timeouts)
		// propagate out of the chain first and only get their 5xx status from the error handler afterwards.
		var logged = new AtomicBoolean();
		Runnable logOnce = () -> {
			if (logged.compareAndSet(false, true)) {
				log.atInfo()
						.addKeyValue("requestId", requestId)
						.addKeyValue("clientId",
								traced.getAttributeOrDefault(ClientIdCaptureFilter.CLIENT_ID_ATTRIBUTE, "-"))
						.addKeyValue("method", traced.getRequest().getMethod())
						.addKeyValue("path", traced.getRequest().getPath().value())
						.addKeyValue("status", traced.getResponse().getStatusCode() == null ? "-"
								: traced.getResponse().getStatusCode().value())
						.addKeyValue("durationMs", (System.nanoTime() - start) / 1_000_000)
						.log("Request completed");
			}
		};
		traced.getResponse().beforeCommit(() -> Mono.fromRunnable(logOnce));
		// A client that disconnects before any response is sent never commits; log it with status "-".
		return chain.filter(traced).doFinally(signal -> {
			if (signal == SignalType.CANCEL) {
				logOnce.run();
			}
		});
	}

	/** Accept a caller-supplied id only if it's short and safe to log. */
	private static boolean isValid(String requestId) {
		return requestId != null && requestId.matches("[A-Za-z0-9._-]{1,64}");
	}
}
