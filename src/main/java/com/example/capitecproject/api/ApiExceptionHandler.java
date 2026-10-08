package com.example.capitecproject.api;

import com.example.capitecproject.service.AccountBusyException;
import com.example.capitecproject.service.AssessmentNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(AssessmentNotFoundException.class)
	ProblemDetail notFound(AssessmentNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(AccountBusyException.class)
	ResponseEntity<ProblemDetail> accountBusy(AccountBusyException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "1")
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception ex) {
		log.atError().setCause(ex).log("Unhandled error");
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
	}
}
