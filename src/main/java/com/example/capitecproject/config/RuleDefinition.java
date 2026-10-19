package com.example.capitecproject.config;

import com.example.capitecproject.domain.TransactionCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One rule from the rule file. Each {@link RuleType} takes a fixed set of parameters: the required ones must be set,
 * and any other parameter is rejected. A mistake therefore stops the service from starting instead of being ignored.
 */
public record RuleDefinition(
		@NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{2,47}", message = "must be upper case letters, digits and '_'") String code,
		@NotNull RuleType type,
		@Positive int score,
		boolean shadow,
		BigDecimal threshold,
		Set<TransactionCategory> categories,
		BigDecimal minAmount,
		Set<String> countries,
		Integer maxTransactions,
		Duration window,
		Integer startHour,
		Integer endHour,
		ZoneId zone) {

	public RuleDefinition {
        Set<String> set = new HashSet<>();
		if (threshold != null) set.add("threshold");
		if (categories != null) set.add("categories");
		if (minAmount != null) set.add("min-amount");
		if (countries != null) set.add("countries");
		if (maxTransactions != null) set.add("max-transactions");
		if (window != null) set.add("window");
		if (startHour != null) set.add("start-hour");
		if (endHour != null) set.add("end-hour");
		if (zone != null) set.add("zone");

		Set<String> required = switch (type) {
			case AMOUNT_THRESHOLD -> Set.of("threshold");
			case CATEGORY_AMOUNT -> Set.of("categories", "min-amount");
			case COUNTRY_LIST -> Set.of("countries");
			case VELOCITY -> Set.of("max-transactions", "window");
			case ODD_HOURS -> Set.of("start-hour", "end-hour", "min-amount", "zone");
		};
		var unexpected = new HashSet<>(set);
		unexpected.removeAll(required);
		if (!unexpected.isEmpty()) {
			throw new IllegalArgumentException(code + ": " + type + " does not take " + unexpected);
		}
		var missing = new HashSet<>(required);
		missing.removeAll(set);
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException(code + ": " + type + " requires " + missing);
		}

		if (threshold != null && threshold.signum() <= 0) throw new IllegalArgumentException(code + ": threshold must be positive");
		if (minAmount != null && minAmount.signum() < 0) throw new IllegalArgumentException(code + ": min-amount must not be negative");
		if (categories != null && categories.isEmpty()) throw new IllegalArgumentException(code + ": categories must not be empty");
		if (countries != null) {
			if (countries.isEmpty()) throw new IllegalArgumentException(code + ": countries must not be empty");
			countries = countries.stream().map(c -> c.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
		}
		if (maxTransactions != null && maxTransactions <= 0) throw new IllegalArgumentException(code + ": max-transactions must be positive");
		if (window != null && (window.isZero() || window.isNegative())) throw new IllegalArgumentException(code + ": window must be positive");
		if (startHour != null && (startHour < 0 || startHour > 23)) throw new IllegalArgumentException(code + ": start-hour must be 0-23");
		if (endHour != null && (endHour < 1 || endHour > 24)) throw new IllegalArgumentException(code + ": end-hour must be 1-24");
	}
}
