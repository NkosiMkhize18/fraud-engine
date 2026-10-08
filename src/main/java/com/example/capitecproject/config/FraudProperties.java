package com.example.capitecproject.config;

import com.example.capitecproject.domain.TransactionCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fraud")
public record FraudProperties(@Positive int flagThreshold, @NotNull Duration accountLockTimeout,
		@Valid @NotNull Rules rules) {

	public record Rules(
			@Valid @NotNull HighAmount highAmount,
			@Valid @NotNull RiskyCategory riskyCategory,
			@Valid @NotNull Velocity velocity,
			@Valid @NotNull HighRiskCountry highRiskCountry,
			@Valid @NotNull OddHours oddHours) {
	}

	public record HighAmount(boolean enabled, @NotNull @Positive BigDecimal threshold, @Positive int score) {
	}

	public record RiskyCategory(boolean enabled, @NotNull Set<TransactionCategory> categories,
			@NotNull BigDecimal minAmount, @Positive int score) {
	}

	public record Velocity(boolean enabled, @Positive int maxTransactions, @NotNull Duration window,
			@Positive int score) {
	}

	public record HighRiskCountry(boolean enabled, @NotNull Set<String> countries, @Positive int score) {
	}

	public record OddHours(boolean enabled, @Min(0) @Max(23) int startHour, @Min(1) @Max(24) int endHour,
			@NotNull BigDecimal minAmount, @Positive int score) {
	}
}
