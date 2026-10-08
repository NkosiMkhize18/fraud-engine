package com.example.gateway.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@ExtendWith(OutputCaptureExtension.class)
class AccessLogFilterTest {

	private final AccessLogFilter filter = new AccessLogFilter();
	private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

	private WebFilterChain respondingWith(HttpStatus status) {
		return exchange -> {
			forwarded.set(exchange);
			exchange.getResponse().setStatusCode(status);
			return exchange.getResponse().setComplete();
		};
	}

	private static MockServerWebExchange get(String path, String requestId) {
		var request = MockServerHttpRequest.get(path);
		if (requestId != null) {
			request.header(AccessLogFilter.REQUEST_ID_HEADER, requestId);
		}
		return MockServerWebExchange.from(request);
	}

	@Test
	void assignsARequestIdAndForwardsAndReturnsIt() {
		var exchange = get("/api/v1/assessments", null);

		filter.filter(exchange, respondingWith(HttpStatus.OK)).block();

		String forwardedId = forwarded.get().getRequest().getHeaders().getFirst(AccessLogFilter.REQUEST_ID_HEADER);
		assertThat(forwardedId).matches("[0-9a-f-]{36}");
		assertThat(exchange.getResponse().getHeaders().getFirst(AccessLogFilter.REQUEST_ID_HEADER))
				.isEqualTo(forwardedId);
	}

	@Test
	void keepsAValidCallerSuppliedRequestId() {
		filter.filter(get("/api/v1/assessments", "caller-1.A_b"), respondingWith(HttpStatus.OK)).block();

		assertThat(forwarded.get().getRequest().getHeaders().getFirst(AccessLogFilter.REQUEST_ID_HEADER))
				.isEqualTo("caller-1.A_b");
	}

	@Test
	void replacesARequestIdThatIsUnsafeToLog() {
		for (String unsafe : new String[] { "x".repeat(65), "has space", "new\nline", "{\"json\":1}" }) {
			filter.filter(get("/api/v1/assessments", unsafe), respondingWith(HttpStatus.OK)).block();

			assertThat(forwarded.get().getRequest().getHeaders().getFirst(AccessLogFilter.REQUEST_ID_HEADER))
					.isNotEqualTo(unsafe).matches("[0-9a-f-]{36}");
		}
	}

	@Test
	void logsOneLineWithTheResponseStatus(CapturedOutput output) {
		filter.filter(get("/api/v1/assessments", "log-ok-1"), respondingWith(HttpStatus.CREATED)).block();

		assertThat(output.getOut().lines().filter(line -> line.contains("requestId=log-ok-1")))
				.singleElement().asString()
				.contains("Request completed", "status=201", "method=GET", "path=/api/v1/assessments", "clientId=-");
	}

	@Test
	void errorsAreLoggedWithTheStatusTheErrorHandlerSetsAfterwards(CapturedOutput output) {
		var exchange = get("/api/v1/assessments/broken", "log-error-1");

		assertThatThrownBy(() -> filter.filter(exchange, ex -> Mono.error(new IllegalStateException("upstream closed")))
				.block()).isInstanceOf(IllegalStateException.class);
		assertThat(output.getOut()).doesNotContain("requestId=log-error-1");

		exchange.getResponse().setStatusCode(HttpStatus.BAD_GATEWAY);
		exchange.getResponse().setComplete().block();

		assertThat(output.getOut().lines().filter(line -> line.contains("requestId=log-error-1")))
				.singleElement().asString().contains("status=502");
	}
}
