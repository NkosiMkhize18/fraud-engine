package com.example.capitecproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.capitecproject.domain.FraudAssessment;
import com.example.capitecproject.domain.RuleHit;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FraudMetricsTest {

	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
	private final FraudMetrics metrics = new FraudMetrics(registry);

	private double count(String name, String... tags) {
		return registry.get(name).tags(tags).counter().count();
	}

	private static FraudEvaluationService.Result created(boolean flagged, int score, String... rules) {
		var hits = new ArrayList<RuleHit>();
		for (String rule : rules) {
			hits.add(new RuleHit(rule, rule, 10));
		}
		return new FraudEvaluationService.Result(FraudAssessment.builder().transactionId("t").flagged(flagged)
				.riskScore(score).ruleHits(hits).build(), true);
	}

	@Test
	void countersStartAtZeroSoDashboardsShowZeroInsteadOfNoData() {
		assertThat(count("fraud.submissions", "outcome", "assessed")).isZero();
		assertThat(count("fraud.submissions", "outcome", "duplicate")).isZero();
		assertThat(count("fraud.submissions", "outcome", "account_busy")).isZero();
		assertThat(count("fraud.assessments", "flagged", "true")).isZero();
		assertThat(count("fraud.assessments", "flagged", "false")).isZero();
	}

	@Test
	void newAssessmentCountsOutcomeVerdictRulesAndScore() {
		metrics.recordResult(created(true, 70, "HIGH_AMOUNT", "RISKY_CATEGORY"));
		metrics.recordResult(created(false, 30, "HIGH_AMOUNT"));

		assertThat(count("fraud.submissions", "outcome", "assessed")).isEqualTo(2);
		assertThat(count("fraud.assessments", "flagged", "true")).isEqualTo(1);
		assertThat(count("fraud.assessments", "flagged", "false")).isEqualTo(1);
		assertThat(count("fraud.rule.hits", "rule", "HIGH_AMOUNT")).isEqualTo(2);
		assertThat(count("fraud.rule.hits", "rule", "RISKY_CATEGORY")).isEqualTo(1);
		var score = registry.get("fraud.risk.score").summary();
		assertThat(score.count()).isEqualTo(2);
		assertThat(score.totalAmount()).isEqualTo(100);
	}

	@Test
	void duplicateIsCountedWithoutTouchingVerdictsOrScores() {
		metrics.recordResult(new FraudEvaluationService.Result(
				FraudAssessment.builder().transactionId("t").flagged(true).ruleHits(new ArrayList<>(List.of())).build(),
				false));

		assertThat(count("fraud.submissions", "outcome", "duplicate")).isEqualTo(1);
		assertThat(count("fraud.assessments", "flagged", "true")).isZero();
		assertThat(registry.get("fraud.risk.score").summary().count()).isZero();
	}

	@Test
	void accountBusyIsCounted() {
		metrics.recordAccountBusy();

		assertThat(count("fraud.submissions", "outcome", "account_busy")).isEqualTo(1);
	}

	@Test
	void evaluationIsTimedAndItsResultReturned() {
		assertThat(metrics.timeEvaluation(() -> "result")).isEqualTo("result");
		assertThat(registry.get("fraud.evaluation").timer().count()).isEqualTo(1);
	}
}
