package com.example.capitecproject.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Service settings. The rules themselves are in {@code fraud-rules.yml}, which is imported into this same
 * {@code fraud} namespace, so a typo in either file fails startup.
 *
 * <p>Rule thresholds are expressed in {@code baseCurrency}. Other currencies are converted with the rates in the
 * {@code exchange_rate} table before any rule runs.
 */
@Validated
@ConfigurationProperties(value = "fraud", ignoreUnknownFields = false)
public record FraudProperties(@Positive int flagThreshold, @NotNull Duration accountLockTimeout,
		@NotBlank @Pattern(regexp = "[A-Z]{3}") String baseCurrency,
		@Valid @NotNull List<RuleDefinition> rules, @Valid @NotNull Timestamps timestamp) {

	public FraudProperties {
		rules = rules == null ? List.of() : List.copyOf(rules);
		Set<String> codes = new HashSet<>();
		for (RuleDefinition rule : rules) {
			if (!codes.add(rule.code())) {
				throw new IllegalArgumentException("Duplicate rule code " + rule.code());
			}
		}
	}

	public record Timestamps(@NotNull Duration maxAge, @NotNull Duration maxFutureSkew) {
	}
}
