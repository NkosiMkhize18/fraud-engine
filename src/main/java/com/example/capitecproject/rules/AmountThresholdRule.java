package com.example.capitecproject.rules;

import com.example.capitecproject.domain.RuleHit;
import java.math.BigDecimal;
import java.util.Optional;

/** Fires when the converted amount is at or above a threshold in the base currency. */
public class AmountThresholdRule implements FraudRule {

	private final String code;
	private final BigDecimal threshold;
	private final int score;

	public AmountThresholdRule(String code, BigDecimal threshold, int score) {
		this.code = code;
		this.threshold = threshold;
		this.score = score;
	}

	@Override
	public Optional<RuleHit> evaluate(EvaluationContext context) {
		if (context.baseAmount().compareTo(threshold) < 0) {
			return Optional.empty();
		}
		return Optional.of(new RuleHit(code,
				"Amount %s is at or above the %s limit".formatted(context.amountDescription(),
						context.baseMoney(threshold)),
				score));
	}
}
