package com.example.capitecproject.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.capitecproject.domain.FraudAssessment;
import com.example.capitecproject.domain.RuleHit;
import com.example.capitecproject.domain.TransactionCategory;
import com.example.capitecproject.service.AssessmentQueryService;
import com.example.capitecproject.service.FraudEvaluationService;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

class FraudControllerTest {

	private final FraudEvaluationService evaluationService = mock(FraudEvaluationService.class);
	private final AssessmentQueryService queryService = mock(AssessmentQueryService.class);
	private final FraudController controller = new FraudController(evaluationService, queryService);

	private static final TransactionRequest REQUEST = new TransactionRequest("t1", "acc-1", new BigDecimal("15000"),
			"ZAR", TransactionCategory.GAMBLING, "Acme", "ZA", Instant.parse("2026-03-01T12:00:00Z"));

	private static FraudAssessment assessment(String id) {
		return FraudAssessment.builder().transactionId(id).accountId("acc-1").amount(new BigDecimal("15000.0000"))
				.currency("ZAR").category(TransactionCategory.GAMBLING).merchant("Acme").country("ZA")
				.transactionTime(Instant.parse("2026-03-01T12:00:00Z")).riskScore(70).flagged(true)
				.ruleHits(new ArrayList<>(List.of(new RuleHit("HIGH_AMOUNT", "amount", 40))))
				.evaluatedAt(Instant.parse("2026-03-01T12:00:01Z")).build();
	}

	@Test
	void newTransactionReturns201WithLocation() {
		when(evaluationService.evaluate(REQUEST.toEvent()))
				.thenReturn(new FraudEvaluationService.Result(assessment("t1"), true));

		var response = controller.submit(REQUEST);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("/api/v1/assessments/t1"));
		assertThat(response.getBody().riskScore()).isEqualTo(70);
	}

	@Test
	void alreadyAssessedTransactionReturns200WithoutLocation() {
		when(evaluationService.evaluate(any())).thenReturn(new FraudEvaluationService.Result(assessment("t1"), false));

		var response = controller.submit(REQUEST);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders().getLocation()).isNull();
	}

	@Test
	void getMapsEveryFieldOfTheAssessment() {
		when(queryService.get("t1")).thenReturn(assessment("t1"));

		assertThat(controller.get("t1")).isEqualTo(new AssessmentResponse("t1", "acc-1", new BigDecimal("15000.0000"),
				"ZAR", TransactionCategory.GAMBLING, "Acme", "ZA", Instant.parse("2026-03-01T12:00:00Z"), 70, true,
				List.of(new RuleHit("HIGH_AMOUNT", "amount", 40)), Instant.parse("2026-03-01T12:00:01Z")));
	}

	@Test
	void searchReturnsThePageWithItsPaging() {
		when(queryService.search(true, "acc-1", null, null, 1, 2)).thenReturn(
				new PageImpl<>(List.of(assessment("t3"), assessment("t2")), PageRequest.of(1, 2), 5));

		var page = controller.search(true, "acc-1", null, null, 1, 2);

		assertThat(page.items()).extracting(AssessmentResponse::transactionId).containsExactly("t3", "t2");
		assertThat(page.page()).isEqualTo(1);
		assertThat(page.size()).isEqualTo(2);
		assertThat(page.totalItems()).isEqualTo(5);
		assertThat(page.totalPages()).isEqualTo(3);
	}

	@Test
	void requestMapsToAnEvent() {
		var event = REQUEST.toEvent();

		assertThat(event.transactionId()).isEqualTo("t1");
		assertThat(event.accountId()).isEqualTo("acc-1");
		assertThat(event.amount()).isEqualByComparingTo("15000");
		assertThat(event.category()).isEqualTo(TransactionCategory.GAMBLING);
		assertThat(event.timestamp()).isEqualTo(Instant.parse("2026-03-01T12:00:00Z"));
	}
}
