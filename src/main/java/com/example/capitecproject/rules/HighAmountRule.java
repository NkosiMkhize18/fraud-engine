package com.example.capitecproject.rules;

import com.example.capitecproject.config.FraudProperties;
import com.example.capitecproject.domain.RuleHit;
import com.example.capitecproject.domain.TransactionEvent;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fraud.rules.high-amount.enabled", havingValue = "true")
@RequiredArgsConstructor
public class HighAmountRule implements FraudRule {

	public static final String CODE = "HIGH_AMOUNT";

	private final FraudProperties properties;

	@Override
	public Optional<RuleHit> evaluate(TransactionEvent event) {
		var config = properties.rules().highAmount();
		if (event.amount().compareTo(config.threshold()) < 0) {
			return Optional.empty();
		}
		return Optional.of(new RuleHit(CODE,
				"Amount %s is at or above the %s limit".formatted(event.amount().toPlainString(),
						config.threshold().toPlainString()),
				config.score()));
	}
}
