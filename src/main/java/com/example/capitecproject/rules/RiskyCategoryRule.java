package com.example.capitecproject.rules;

import com.example.capitecproject.config.FraudProperties;
import com.example.capitecproject.domain.RuleHit;
import com.example.capitecproject.domain.TransactionEvent;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fraud.rules.risky-category.enabled", havingValue = "true")
@RequiredArgsConstructor
public class RiskyCategoryRule implements FraudRule {

	public static final String CODE = "RISKY_CATEGORY";

	private final FraudProperties properties;

	@Override
	public Optional<RuleHit> evaluate(TransactionEvent event) {
		var config = properties.rules().riskyCategory();
		if (!config.categories().contains(event.category())
				|| event.amount().compareTo(config.minAmount()) < 0) {
			return Optional.empty();
		}
		return Optional.of(new RuleHit(CODE,
				"%s transaction of %s is at or above the %s risky-category limit".formatted(event.category(),
						event.amount().toPlainString(), config.minAmount().toPlainString()),
				config.score()));
	}
}
