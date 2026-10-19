package com.example.capitecproject.api;

import com.example.capitecproject.exception.AccountBusyException;
import com.example.capitecproject.exception.AssessmentNotFoundException;
import com.example.capitecproject.exception.DuplicateTransactionException;
import com.example.capitecproject.exception.InvalidTransactionException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(AssessmentNotFoundException.class)
	ProblemDetail notFound(AssessmentNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(InvalidTransactionException.class)
	ProblemDetail invalid(InvalidTransactionException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(DuplicateTransactionException.class)
	ProblemDetail conflict(DuplicateTransactionException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(AccountBusyException.class)
	ResponseEntity<ProblemDetail> accountBusy(AccountBusyException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "1")
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
	}

	/** The pool couldn't hand out a connection in time (usually a burst on hot accounts). Nothing was stored. */
	@ExceptionHandler(CannotGetJdbcConnectionException.class)
	ResponseEntity<ProblemDetail> databaseBusy(CannotGetJdbcConnectionException ex) {
		log.atWarn().setCause(ex).log("Database connection unavailable");
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "1")
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
						"The service is busy; retry shortly"));
	}

	/** Names each invalid field, so a client can tell what to fix. The default message is just "Invalid request content." */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status, @NonNull WebRequest request) {
		var errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.sorted()
				.toList();
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request: " + String.join("; ", errors));
		problem.setProperty("errors", errors);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception ex) {
		log.atError().setCause(ex).log("Unhandled error");
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
	}
}
