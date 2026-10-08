package com.example.capitecproject.rules;

import com.example.capitecproject.config.FraudProperties;
import com.example.capitecproject.domain.RuleHit;
import com.example.capitecproject.domain.TransactionEvent;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fraud.rules.high-risk-country.enabled", havingValue = "true")
public class HighRiskCountryRule implements FraudRule {

	public static final String CODE = "HIGH_RISK_COUNTRY";

	private final Set<String> countries;
	private final int score;

	public HighRiskCountryRule(FraudProperties properties) {
		var config = properties.rules().highRiskCountry();
		this.countries = config.countries().stream().map(country -> country.toUpperCase()).collect(Collectors.toUnmodifiableSet());
		this.score = config.score();
	}

	@Override
	public Optional<RuleHit> evaluate(TransactionEvent event) {
		if (!countries.contains(event.country().toUpperCase())) {
			return Optional.empty();
		}
		return Optional.of(new RuleHit(CODE, "Transaction originated in high-risk country " + event.country(), score));
	}
}
