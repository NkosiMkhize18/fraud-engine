package com.example.capitecproject.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

	private final RequestIdFilter filter = new RequestIdFilter();

	@Test
	void reusesAValidIncomingIdInLogsAndOnTheResponse() throws Exception {
		var request = new MockHttpServletRequest();
		request.addHeader("X-Request-Id", "gw-abc.123");
		var response = new MockHttpServletResponse();
		String[] seenInChain = new String[1];
		FilterChain chain = (req, res) -> seenInChain[0] = MDC.get("requestId");

		filter.doFilter(request, response, chain);

		assertThat(seenInChain[0]).isEqualTo("gw-abc.123");
		assertThat(response.getHeader("X-Request-Id")).isEqualTo("gw-abc.123");
		assertThat(MDC.get("requestId")).isNull();
	}

	@Test
	void replacesAnInvalidIncomingIdWithAGeneratedOne() throws Exception {
		var request = new MockHttpServletRequest();
		request.addHeader("X-Request-Id", "bad id with spaces\n");
		var response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getHeader("X-Request-Id")).isNotBlank().doesNotContain(" ").doesNotContain("\n");
	}

	@Test
	void clearsTheLoggingContextEvenWhenTheChainFails() {
		var request = new MockHttpServletRequest();
		FilterChain failing = (req, res) -> {
			throw new IllegalStateException("boom");
		};

		try {
			filter.doFilter(request, new MockHttpServletResponse(), failing);
		} catch (Exception expected) {
			// the failure propagates; only the MDC cleanup matters here
		}

		assertThat(MDC.get("requestId")).isNull();
	}
}
