package com.example.capitecproject.api;

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
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Fraud assessments")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class FraudController {

	static final int MAX_PAGE = 10_000;

	private final FraudEvaluationService evaluationService;
	private final AssessmentQueryService queryService;

	@Operation(summary = "Submit a transaction for assessment",
			security = @SecurityRequirement(name = OpenApiConfig.SCHEME, scopes = "fraud-api/submit"))
	@ApiResponse(responseCode = "201", description = "Newly assessed")
	@ApiResponse(responseCode = "200", description = "Already assessed; the stored assessment is returned")
	@ApiResponse(responseCode = "400", description = "Invalid request")
	@ApiResponse(responseCode = "429", description = "Too many concurrent requests for this account; retry after the Retry-After delay")
	@PostMapping("/transactions")
	public ResponseEntity<AssessmentResponse> submit(@Valid @RequestBody TransactionRequest request) {
		var result = evaluationService.evaluate(request.toEvent());
		var body = AssessmentResponse.from(result.assessment());
		if (!result.created()) {
			return ResponseEntity.ok(body);
		}
		return ResponseEntity.created(URI.create("/api/v1/assessments/" + body.transactionId())).body(body);
	}

	@Operation(summary = "Get one assessment",
			security = @SecurityRequirement(name = OpenApiConfig.SCHEME, scopes = "fraud-api/read"))
	@ApiResponse(responseCode = "200", description = "The assessment")
	@ApiResponse(responseCode = "404", description = "No assessment for this transaction id")
	@GetMapping("/assessments/{transactionId}")
	public AssessmentResponse get(@PathVariable String transactionId) {
		return AssessmentResponse.from(queryService.get(transactionId));
	}

	@Operation(summary = "Search assessments, newest first",
			security = @SecurityRequirement(name = OpenApiConfig.SCHEME, scopes = "fraud-api/read"))
	@GetMapping("/assessments")
	public PageResponse<AssessmentResponse> search(
			@RequestParam(required = false) Boolean flagged,
			@RequestParam(required = false) String accountId,
			@Parameter(description = "Inclusive, ISO-8601") @RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
			@Parameter(description = "Exclusive, ISO-8601") @RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
			@RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.of(queryService.search(flagged, accountId, from, to, page, size),
				AssessmentResponse::from);
	}
}
