package com.example.capitecproject.rules;

import com.example.capitecproject.config.FraudProperties;
import com.example.capitecproject.domain.RuleHit;
import com.example.capitecproject.domain.TransactionEvent;
import com.example.capitecproject.repository.FraudAssessmentRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Flags an account that transacts more often than allowed within a sliding time
 * window.
 */
@Component
@ConditionalOnProperty(name = "fraud.rules.velocity.enabled", havingValue = "true")
@RequiredArgsConstructor
public class VelocityRule implements FraudRule {

	public static final String CODE = "VELOCITY";

	private final FraudProperties properties;
	private final FraudAssessmentRepository repository;

	@Override
	public Optional<RuleHit> evaluate(TransactionEvent event) {
		var config = properties.rules().velocity();
		Instant from = event.timestamp().minus(config.window());
		long count = repository.countByAccountIdAndTransactionTimeBetween(event.accountId(), from,
				event.timestamp()) + 1;
		if (count <= config.maxTransactions()) {
			return Optional.empty();
		}
		return Optional.of(new RuleHit(CODE,
				"%d transactions within %s exceeds the limit of %d".formatted(count, config.window(),
						config.maxTransactions()),
				config.score()));
	}
}
