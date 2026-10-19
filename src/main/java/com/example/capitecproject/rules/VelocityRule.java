package com.example.capitecproject.rules;

import com.example.capitecproject.domain.RuleHit;
import com.example.capitecproject.repository.FraudAssessmentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Fires when the account has more than {@code maxTransactions} assessments in the window. Counts stored assessments by
 * arrival time, so a client can't avoid it by sending old timestamps. Runs under the account lock.
 */
public class VelocityRule implements FraudRule {

	private final String code;
	private final int maxTransactions;
	private final Duration window;
	private final int score;
	private final FraudAssessmentRepository repository;
	private final Clock clock;

	public VelocityRule(String code, int maxTransactions, Duration window, int score,
			FraudAssessmentRepository repository, Clock clock) {
		this.code = code;
		this.maxTransactions = maxTransactions;
		this.window = window;
		this.score = score;
		this.repository = repository;
		this.clock = clock;
	}

	@Override
	public Optional<RuleHit> evaluate(EvaluationContext context) {
		OffsetDateTime now = clock.instant().atOffset(ZoneOffset.UTC);
		long count = repository.countByAccountIdAndEvaluatedAtBetween(context.event().accountId(), now.minus(window),
				now) + 1;
		if (count <= maxTransactions) {
			return Optional.empty();
		}
		return Optional.of(new RuleHit(code,
				"%d transactions within %s exceeds the limit of %d".formatted(count, window, maxTransactions),
				score));
	}
}
