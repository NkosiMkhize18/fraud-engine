package com.example.capitecproject.service;

import com.example.capitecproject.domain.FraudAssessment;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class FraudMetrics {

	private final MeterRegistry registry;
	private final Counter assessed;
	private final Counter duplicate;
	private final Counter accountBusy;
	private final Counter flagged;
	private final Counter notFlagged;
	private final DistributionSummary riskScore;
	private final Timer evaluation;

	public FraudMetrics(MeterRegistry registry) {
		this.registry = registry;
		assessed = submissions(registry, "assessed");
		duplicate = submissions(registry, "duplicate");
		accountBusy = submissions(registry, "account_busy");
		flagged = assessments(registry, true);
		notFlagged = assessments(registry, false);
		riskScore = DistributionSummary.builder("fraud.risk.score")
				.description("Risk score of each new assessment")
				.serviceLevelObjectives(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 150)
				.register(registry);
		evaluation = Timer.builder("fraud.evaluation")
				.description("Time to evaluate a submission, including waiting for the account lock")
				.publishPercentileHistogram()
				.register(registry);
	}

	private static Counter submissions(MeterRegistry registry, String outcome) {
		return Counter.builder("fraud.submissions").description("Transaction submissions by outcome")
				.tag("outcome", outcome).register(registry);
	}

	private static Counter assessments(MeterRegistry registry, boolean flagged) {
		return Counter.builder("fraud.assessments").description("New assessments, by whether they were flagged")
				.tag("flagged", String.valueOf(flagged)).register(registry);
	}

	<T> T timeEvaluation(Supplier<T> evaluation) {
		return this.evaluation.record(evaluation);
	}

	void recordResult(FraudEvaluationService.Result result) {
		if (!result.created()) {
			duplicate.increment();
			return;
		}
		FraudAssessment assessment = result.assessment();
		assessed.increment();
		(assessment.isFlagged() ? flagged : notFlagged).increment();
		riskScore.record(assessment.getRiskScore());
		assessment.getRuleHits().forEach(hit -> Counter.builder("fraud.rule.hits")
				.description("Rules triggered by new assessments").tag("rule", hit.ruleCode())
				.register(registry).increment());
	}

	void recordAccountBusy() {
		accountBusy.increment();
	}
}
