package com.example.capitecproject.api;

import com.example.capitecproject.domain.FraudAssessment;
import com.example.capitecproject.exception.AccountBusyException;
import com.example.capitecproject.exception.AssessmentNotFoundException;
import com.example.capitecproject.exception.DuplicateTransactionException;
import com.example.capitecproject.exception.InvalidTransactionException;
import com.example.capitecproject.service.AssessmentQueryService;
import com.example.capitecproject.service.FraudEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Tag(name = "Fraud assessments")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Slf4j
public class FraudController {

	static final int MAX_PAGE = 10_000;

	private final FraudEvaluationService evaluationService;
	private final AssessmentQueryService queryService;
	private final AuditLog audit;

	@Operation(summary = "Submit a transaction for assessment",
			security = @SecurityRequirement(name = OpenApiConfig.SCHEME, scopes = "fraud-api/submit"))
	@ApiResponse(responseCode = "201", description = "Newly assessed")
	@ApiResponse(responseCode = "200", description = "Already assessed; the stored assessment is returned")
	@ApiResponse(responseCode = "400", description = "Invalid request, a timestamp outside the accepted window, or a currency with no exchange rate")
	@ApiResponse(responseCode = "409", description = "This transactionId was already submitted with different details")
	@ApiResponse(responseCode = "429", description = "Too many concurrent requests for this account; retry after the Retry-After delay")
	@ApiResponse(responseCode = "503", description = "The database is busy; retry after the Retry-After delay")
	@PostMapping("/transactions")
	public ResponseEntity<AssessmentResponse> submit(@Valid @RequestBody TransactionRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		String actor = actor(jwt);
		log.atInfo()
				.addKeyValue("Account-Id", request.accountId())
				.addKeyValue("Transaction-Id", request.transactionId())
				.log("Received Transaction Request to evaluate");
		FraudEvaluationService.Result result;
		try {
			result = evaluationService.evaluate(request.toEvent());
		} catch (RuntimeException ex) {
			audit.record(actor, "SUBMIT", request.transactionId(), outcomeOf(ex), null);
			throw ex;
		}
		var assessment = result.assessment();
		audit.record(actor, "SUBMIT", request.transactionId(), result.created() ? "CREATED" : "REPLAYED",
				"score=%d flagged=%s".formatted(assessment.getRiskScore(), assessment.isFlagged()));
		var body = AssessmentResponse.from(assessment);
		if (!result.created()) {
			return ResponseEntity.ok(body);
		}
		URI location = UriComponentsBuilder.fromPath("/api/v1/assessments/{id}").buildAndExpand(body.transactionId()).encode().toUri();
		return ResponseEntity.created(location).body(body);
	}

	@Operation(summary = "Get one assessment",
			security = @SecurityRequirement(name = OpenApiConfig.SCHEME, scopes = "fraud-api/read"))
	@ApiResponse(responseCode = "200", description = "The assessment")
	@ApiResponse(responseCode = "404", description = "No assessment for this transaction id")
	@GetMapping("/assessments/{transactionId}")
	public AssessmentResponse get(@PathVariable String transactionId, @AuthenticationPrincipal Jwt jwt) {
		String actor = actor(jwt);
		FraudAssessment assessment;
		try {
			assessment = queryService.get(transactionId);
		} catch (RuntimeException ex) {
			audit.record(actor, "READ", transactionId, outcomeOf(ex), null);
			throw ex;
		}
		audit.record(actor, "READ", transactionId, "OK", null);
		return AssessmentResponse.from(assessment);
	}

	@Operation(summary = "Search assessments, newest first",
			security = @SecurityRequirement(name = OpenApiConfig.SCHEME, scopes = "fraud-api/read"))
	@GetMapping("/assessments")
	public PageResponse<AssessmentResponse> search(
			@RequestParam(required = false) Boolean flagged,
			@RequestParam(required = false) String accountId,
			@Parameter(description = "Inclusive, ISO-8601 with offset, e.g. 2026-03-01T00:00:00Z") @RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
			@Parameter(description = "Exclusive, ISO-8601 with offset, e.g. 2026-03-02T00:00:00Z") @RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
			@RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@AuthenticationPrincipal Jwt jwt) {
		String actor = actor(jwt);
		String filters = "flagged=%s accountId=%s page=%d size=%d".formatted(flagged, accountId, page, size);
		PageResponse<AssessmentResponse> result;
		try {
			result = PageResponse.of(queryService.search(flagged, accountId, from, to, page, size),
					AssessmentResponse::from);
		} catch (RuntimeException ex) {
			audit.record(actor, "SEARCH", null, outcomeOf(ex), filters);
			throw ex;
		}
		audit.record(actor, "SEARCH", null, "OK", filters);
		return result;
	}

	/** The client the token was issued to. Client-credentials tokens carry it as {@code azp}. */
	static String actor(Jwt jwt) {
		String client = jwt.getClaimAsString("azp");
		return client != null ? client : jwt.getSubject();
	}

	/** Maps a failure to the outcome recorded in the audit trail. */
	static String outcomeOf(RuntimeException ex) {
		return switch (ex) {
			case DuplicateTransactionException e -> "CONFLICT";
			case InvalidTransactionException e -> "REJECTED";
			case AccountBusyException e -> "BUSY";
			case AssessmentNotFoundException e -> "NOT_FOUND";
			case CannotGetJdbcConnectionException e -> "UNAVAILABLE";
			default -> "ERROR";
		};
	}
}
