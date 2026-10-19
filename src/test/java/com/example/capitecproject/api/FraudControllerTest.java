package com.example.capitecproject.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.capitecproject.domain.FraudAssessment;
import com.example.capitecproject.domain.RuleHit;
import com.example.capitecproject.domain.TransactionCategory;
import com.example.capitecproject.exception.AccountBusyException;
import com.example.capitecproject.exception.AssessmentNotFoundException;
import com.example.capitecproject.exception.DuplicateTransactionException;
import com.example.capitecproject.exception.InvalidTransactionException;
import com.example.capitecproject.service.AssessmentQueryService;
import com.example.capitecproject.service.FraudEvaluationService;
import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class FraudControllerTest {

	private final FraudEvaluationService evaluationService = mock(FraudEvaluationService.class);
	private final AssessmentQueryService queryService = mock(AssessmentQueryService.class);
	private final AuditLog audit = mock(AuditLog.class);
	private final FraudController controller = new FraudController(evaluationService, queryService, audit);

	private static final Jwt CLIENT = Jwt.withTokenValue("token").header("alg", "none")
			.subject("service-account-1").claim("azp", "transaction-submitter").build();

	private static final TransactionRequest REQUEST = new TransactionRequest("t1", "acc-1", new BigDecimal("15000"),
			"ZAR", TransactionCategory.GAMBLING, "Acme", "ZA", OffsetDateTime.parse("2026-03-01T12:00:00Z"));

	private static FraudAssessment assessment(String id) {
		return FraudAssessment.builder().transactionId(id).accountId("acc-1").amount(new BigDecimal("15000.0000"))
				.currency("ZAR").baseAmount(new BigDecimal("15000.0000")).rateToBase(BigDecimal.ONE)
				.category(TransactionCategory.GAMBLING).merchant("Acme").country("ZA")
				.transactionTime(OffsetDateTime.parse("2026-03-01T12:00:00Z")).riskScore(70).flagged(true)
				.ruleHits(new ArrayList<>(List.of(new RuleHit("HIGH_AMOUNT", "amount", 40))))
				.evaluatedAt(OffsetDateTime.parse("2026-03-01T12:00:01Z")).build();
	}

	@Test
	void newTransactionReturns201WithLocation() {
		when(evaluationService.evaluate(REQUEST.toEvent()))
				.thenReturn(new FraudEvaluationService.Result(assessment("t1"), true));

		var response = controller.submit(REQUEST, CLIENT);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/api/v1/assessments/t1"));
		assertThat(response.getBody().riskScore()).isEqualTo(70);
	}

	@Test
	void locationIsBuiltFromTheIdWithoutMangling() {
		var dotted = new TransactionRequest("txn.2026-03_01-a", "acc-1", new BigDecimal("15000"), "ZAR",
				TransactionCategory.GAMBLING, "Acme", "ZA", OffsetDateTime.parse("2026-03-01T12:00:00Z"));
		when(evaluationService.evaluate(any())).thenReturn(new FraudEvaluationService.Result(assessment("txn.2026-03_01-a"), true));

		var response = controller.submit(dotted, CLIENT);

		assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/api/v1/assessments/txn.2026-03_01-a"));
	}

	@Test
	void alreadyAssessedTransactionReturns200WithoutLocation() {
		when(evaluationService.evaluate(any())).thenReturn(new FraudEvaluationService.Result(assessment("t1"), false));

		var response = controller.submit(REQUEST, CLIENT);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().getLocation()).isNull();
	}

	@Test
	void submitIsAuditedWithTheClientAndOutcome() {
		when(evaluationService.evaluate(any())).thenReturn(new FraudEvaluationService.Result(assessment("t1"), true));

		controller.submit(REQUEST, CLIENT);

		verify(audit).record("transaction-submitter", "SUBMIT", "t1", "CREATED", "score=70 flagged=true");
	}

	@Test
	void replayIsAuditedAsReplayed() {
		when(evaluationService.evaluate(any())).thenReturn(new FraudEvaluationService.Result(assessment("t1"), false));

		controller.submit(REQUEST, CLIENT);

		verify(audit).record("transaction-submitter", "SUBMIT", "t1", "REPLAYED", "score=70 flagged=true");
	}

	@Test
	void rejectedSubmitIsAuditedAndTheErrorStillReachesTheClient() {
		when(evaluationService.evaluate(any())).thenThrow(new DuplicateTransactionException("t1"));

		assertThatThrownBy(() -> controller.submit(REQUEST, CLIENT)).isInstanceOf(DuplicateTransactionException.class);

		verify(audit).record("transaction-submitter", "SUBMIT", "t1", "CONFLICT", null);
	}

	@Test
	void actorFallsBackToTheSubjectWhenThereIsNoClientClaim() {
		var noAzp = Jwt.withTokenValue("token").header("alg", "none").subject("user-9").build();
		when(evaluationService.evaluate(any())).thenReturn(new FraudEvaluationService.Result(assessment("t1"), true));

		controller.submit(REQUEST, noAzp);

		verify(audit).record("user-9", "SUBMIT", "t1", "CREATED", "score=70 flagged=true");
	}

	@Test
	void outcomesForFailuresAreClassified() {
		assertThat(FraudController.outcomeOf(new InvalidTransactionException("x"))).isEqualTo("REJECTED");
		assertThat(FraudController.outcomeOf(new AccountBusyException(new RuntimeException()))).isEqualTo("BUSY");
		assertThat(FraudController.outcomeOf(new AssessmentNotFoundException("t1"))).isEqualTo("NOT_FOUND");
		assertThat(FraudController.outcomeOf(new IllegalStateException())).isEqualTo("ERROR");
	}

	@Test
	void getMapsEveryFieldOfTheAssessment() {
		when(queryService.get("t1")).thenReturn(assessment("t1"));

		assertThat(controller.get("t1", CLIENT)).isEqualTo(new AssessmentResponse("t1", "acc-1", new BigDecimal("15000.0000"),
				"ZAR", new BigDecimal("15000.0000"), BigDecimal.ONE, TransactionCategory.GAMBLING, "Acme", "ZA", OffsetDateTime.parse("2026-03-01T12:00:00Z"), 70, true,
				List.of(new RuleHit("HIGH_AMOUNT", "amount", 40)), OffsetDateTime.parse("2026-03-01T12:00:01Z"), null));
	}

	@Test
	void readsAreAuditedIncludingNotFound() {
		when(queryService.get("t1")).thenReturn(assessment("t1"));
		when(queryService.get("missing")).thenThrow(new AssessmentNotFoundException("missing"));

		controller.get("t1", CLIENT);
		assertThatThrownBy(() -> controller.get("missing", CLIENT)).isInstanceOf(AssessmentNotFoundException.class);

		verify(audit).record("transaction-submitter", "READ", "t1", "OK", null);
		verify(audit).record("transaction-submitter", "READ", "missing", "NOT_FOUND", null);
	}

	@Test
	void searchReturnsThePageWithItsPagingAndIsAudited() {
		when(queryService.search(true, "acc-1", null, null, 1, 2)).thenReturn(
				new PageImpl<>(List.of(assessment("t3"), assessment("t2")), PageRequest.of(1, 2), 5));

		var page = controller.search(true, "acc-1", null, null, 1, 2, CLIENT);

		assertThat(page.items()).extracting(AssessmentResponse::transactionId).containsExactly("t3", "t2");
		assertThat(page.page()).isEqualTo(1);
		assertThat(page.size()).isEqualTo(2);
		assertThat(page.totalItems()).isEqualTo(5);
		assertThat(page.totalPages()).isEqualTo(3);
		verify(audit).record("transaction-submitter", "SEARCH", null, "OK", "flagged=true accountId=acc-1 page=1 size=2");
	}

	@Test
	void requestMapsToAnEvent() {
		var event = REQUEST.toEvent();

		assertThat(event.transactionId()).isEqualTo("t1");
		assertThat(event.accountId()).isEqualTo("acc-1");
		assertThat(event.amount()).isEqualByComparingTo("15000");
		assertThat(event.category()).isEqualTo(TransactionCategory.GAMBLING);
		assertThat(event.timestamp()).isEqualTo(OffsetDateTime.parse("2026-03-01T12:00:00Z"));
	}
}
