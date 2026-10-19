package com.example.capitecproject.rules;

import com.example.capitecproject.domain.RuleHit;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Fires when the transaction's country is one of the listed codes. Codes are compared case-insensitively. */
public class CountryListRule implements FraudRule {

	private final String code;
	private final Set<String> countries;
	private final int score;

	public CountryListRule(String code, Set<String> countries, int score) {
		this.code = code;
		this.countries = countries.stream().map(country -> country.toUpperCase(Locale.ROOT))
				.collect(java.util.stream.Collectors.toUnmodifiableSet());
		this.score = score;
	}

	@Override
	public Optional<RuleHit> evaluate(EvaluationContext context) {
		String country = context.event().country();
		if (!countries.contains(country.toUpperCase(Locale.ROOT))) {
			return Optional.empty();
		}
		return Optional.of(new RuleHit(code, "Transaction originated in high-risk country " + country, score));
	}
}
