package com.example.capitecproject.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.capitecproject.config.FraudProperties;
import com.example.capitecproject.domain.TransactionCategory;
import com.example.capitecproject.domain.TransactionEvent;
import com.example.capitecproject.repository.FraudAssessmentRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RulesTest {

	private static final Instant NOON = Instant.parse("2026-03-01T12:00:00Z");

	private static TransactionEvent event(String amount, TransactionCategory category, String country, Instant at) {
		return new TransactionEvent("t1", "acc1", new BigDecimal(amount), "ZAR", category, "Shop", country, at);
	}

	private static FraudProperties props(FraudProperties.OddHours oddHours, FraudProperties.Velocity velocity) {
		return new FraudProperties(50, Duration.ofSeconds(1), new FraudProperties.Rules(
				new FraudProperties.HighAmount(true, new BigDecimal("10000"), 40),
				new FraudProperties.RiskyCategory(true, Set.of(TransactionCategory.GAMBLING), new BigDecimal("1000"),
						30),
				velocity,
				new FraudProperties.HighRiskCountry(true, Set.of("KP"), 30),
				oddHours));
	}

	private static FraudProperties defaults() {
		return props(new FraudProperties.OddHours(true, 0, 5, new BigDecimal("2000"), 20),
				new FraudProperties.Velocity(true, 3, Duration.ofMinutes(10), 35));
	}

	@Test
	void highAmountFiresAtThresholdInclusive() {
		var rule = new HighAmountRule(defaults());
		assertThat(rule.evaluate(event("10000", TransactionCategory.RETAIL, "ZA", NOON))).isPresent();
		assertThat(rule.evaluate(event("9999.99", TransactionCategory.RETAIL, "ZA", NOON))).isEmpty();
	}

	@Test
	void riskyCategoryNeedsBothCategoryAndAmount() {
		var rule = new RiskyCategoryRule(defaults());
		assertThat(rule.evaluate(event("1000", TransactionCategory.GAMBLING, "ZA", NOON))).isPresent();
		assertThat(rule.evaluate(event("999", TransactionCategory.GAMBLING, "ZA", NOON))).isEmpty();
		assertThat(rule.evaluate(event("5000", TransactionCategory.GROCERIES, "ZA", NOON))).isEmpty();
	}

	@Test
	void highRiskCountryIsCaseInsensitive() {
		var rule = new HighRiskCountryRule(defaults());
		assertThat(rule.evaluate(event("10", TransactionCategory.RETAIL, "KP", NOON))).isPresent();
		assertThat(rule.evaluate(event("10", TransactionCategory.RETAIL, "kp", NOON))).isPresent();
		assertThat(rule.evaluate(event("10", TransactionCategory.RETAIL, "ZA", NOON))).isEmpty();
	}

	@Test
	void oddHoursWindowIsStartInclusiveEndExclusive() {
		var rule = new OddHoursRule(defaults());
		assertThat(rule.evaluate(event("2000", TransactionCategory.RETAIL, "ZA", Instant.parse("2026-03-01T00:00:00Z"))))
				.isPresent();
		assertThat(rule.evaluate(event("2000", TransactionCategory.RETAIL, "ZA", Instant.parse("2026-03-01T04:59:59Z"))))
				.isPresent();
		assertThat(rule.evaluate(event("2000", TransactionCategory.RETAIL, "ZA", Instant.parse("2026-03-01T05:00:00Z"))))
				.isEmpty();
		assertThat(rule.evaluate(event("1999", TransactionCategory.RETAIL, "ZA", Instant.parse("2026-03-01T02:00:00Z"))))
				.isEmpty();
	}

	@Test
	void oddHoursWindowMayWrapPastMidnight() {
		var wrapping = props(new FraudProperties.OddHours(true, 22, 4, new BigDecimal("0.01"), 20),
				new FraudProperties.Velocity(true, 3, Duration.ofMinutes(10), 35));
		var rule = new OddHoursRule(wrapping);
		assertThat(rule.evaluate(event("10", TransactionCategory.RETAIL, "ZA", Instant.parse("2026-03-01T23:00:00Z"))))
				.isPresent();
		assertThat(rule.evaluate(event("10", TransactionCategory.RETAIL, "ZA", Instant.parse("2026-03-01T03:00:00Z"))))
				.isPresent();
		assertThat(rule.evaluate(event("10", TransactionCategory.RETAIL, "ZA", NOON))).isEmpty();
	}

	@Test
	void velocityCountsPriorTransactionsPlusCurrent() {
		var repo = mock(FraudAssessmentRepository.class);
		var rule = new VelocityRule(defaults(), repo); // max 3 per 10 minutes
		when(repo.countByAccountIdAndTransactionTimeBetween(eq("acc1"), eq(NOON.minus(Duration.ofMinutes(10))),
				eq(NOON))).thenReturn(2L, 3L);

		assertThat(rule.evaluate(event("10", TransactionCategory.RETAIL, "ZA", NOON))).isEmpty(); // 3rd, allowed
		assertThat(rule.evaluate(event("10", TransactionCategory.RETAIL, "ZA", NOON))).isPresent(); // 4th
		when(repo.countByAccountIdAndTransactionTimeBetween(any(), any(), any())).thenReturn(0L);
		assertThat(rule.evaluate(event("10", TransactionCategory.RETAIL, "ZA", NOON.plusSeconds(1)))).isEmpty();
	}
}
