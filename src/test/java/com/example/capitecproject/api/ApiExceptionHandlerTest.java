package com.example.capitecproject.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.capitecproject.service.AccountBusyException;
import com.example.capitecproject.service.AssessmentNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

class ApiExceptionHandlerTest {

	private final ApiExceptionHandler handler = new ApiExceptionHandler();

	@Test
	void unknownAssessmentIs404() {
		var problem = handler.notFound(new AssessmentNotFoundException("t1"));

		assertThat(problem.getStatus()).isEqualTo(404);
		assertThat(problem.getDetail()).isEqualTo("No assessment found for transaction t1");
	}

	@Test
	void accountBusyIs429WithRetryAfter() {
		var response = handler.accountBusy(new AccountBusyException(new RuntimeException("lock timeout")));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
		assertThat(response.getBody().getStatus()).isEqualTo(429);
	}

	@Test
	void unexpectedErrorIs500WithoutLeakingDetails() {
		var problem = handler.unexpected(new IllegalStateException("SELECT * FROM fraud_assessment ... secret"));

		assertThat(problem.getStatus()).isEqualTo(500);
		assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
	}
}
