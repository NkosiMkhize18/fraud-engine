package com.example.capitecproject.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.capitecproject.exception.AccountBusyException;
import com.example.capitecproject.exception.AssessmentNotFoundException;
import com.example.capitecproject.exception.DuplicateTransactionException;
import com.example.capitecproject.exception.InvalidTransactionException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

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
	void databaseConnectionExhaustionIs503WithRetryAfter() {
		var response = handler.databaseBusy(new CannotGetJdbcConnectionException("pool exhausted"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
		assertThat(response.getBody().getDetail()).doesNotContain("pool");
	}

	@Test
	void invalidTransactionIs400() {
		var problem = handler.invalid(new InvalidTransactionException("Unsupported currency XYZ"));

		assertThat(problem.getStatus()).isEqualTo(400);
		assertThat(problem.getDetail()).isEqualTo("Unsupported currency XYZ");
	}

	@Test
	void reusedTransactionIdWithDifferentDetailsIs409() {
		var problem = handler.conflict(new DuplicateTransactionException("t1"));

		assertThat(problem.getStatus()).isEqualTo(409);
		assertThat(problem.getDetail()).isEqualTo("Transaction t1 was already submitted with different details");
	}

	@Test
	void unexpectedErrorIs500WithoutLeakingDetails() {
		var problem = handler.unexpected(new IllegalStateException("SELECT * FROM fraud_assessment ... secret"));

		assertThat(problem.getStatus()).isEqualTo(500);
		assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
	}

	@Test
	void validationErrorsNameEachInvalidField() throws Exception {
		var binding = new BeanPropertyBindingResult(new Object(), "request");
		binding.addError(new FieldError("request", "currency", "must be a 3-letter ISO 4217 code in upper case"));
		binding.addError(new FieldError("request", "country", "must be a 2-letter ISO 3166 code"));
		var parameter = new MethodParameter(getClass().getDeclaredMethod("placeholder", String.class), 0);

		var response = handler.handleMethodArgumentNotValid(new MethodArgumentNotValidException(parameter, binding),
				new HttpHeaders(), HttpStatus.BAD_REQUEST, null);

		var problem = (ProblemDetail) response.getBody();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(problem.getDetail()).isEqualTo("Invalid request: country: must be a 2-letter ISO 3166 code; "
				+ "currency: must be a 3-letter ISO 4217 code in upper case");
	}

	@SuppressWarnings("unused")
	private void placeholder(String ignored) {
	}
}
