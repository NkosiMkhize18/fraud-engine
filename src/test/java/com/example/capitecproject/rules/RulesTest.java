package com.example.capitecproject.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.capitecproject.domain.RuleHit;
import com.example.capitecproject.domain.TransactionCategory;
import com.example.capitecproject.domain.TransactionEvent;
import com.example.capitecproject.repository.FraudAssessmentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RulesTest {

	private static final OffsetDateTime NOON = OffsetDateTime.parse("2026-03-01T12:00:00Z");

	private static TransactionEvent event(String amount, String currency, TransactionCategory category, String country,
			OffsetDateTime at) {
		return new TransactionEvent("t1", "acc1", new BigDecimal(amount), currency, category, "Shop", country, at);
	}

	/** A ZAR event, where the base amount is the submitted amount. */
	private static EvaluationContext zar(String amount, TransactionCategory category, String country, OffsetDateTime at) {
		return new EvaluationContext(event(amount, "ZAR", category, country, at), new BigDecimal(amount), "ZAR");
	}

	private static Optional<RuleHit> run(FraudRule rule, String amount, TransactionCategory category, String country,
			OffsetDateTime at) {
		return rule.evaluate(zar(amount, category, country, at));
	}

	private static FraudRule highAmount() {
		return new AmountThresholdRule("HIGH_AMOUNT", new BigDecimal("10000"), 40);
	}

	private static FraudRule riskyCategory() {
		return new CategoryAmountRule("RISKY_CATEGORY", Set.of(TransactionCategory.GAMBLING, TransactionCategory.CRYPTO),
				new BigDecimal("1000"), 30);
	}

	private static FraudRule countries(String... codes) {
		return new CountryListRule("HIGH_RISK_COUNTRY", Set.of(codes), 30);
	}

	private static FraudRule oddHours(int start, int end, ZoneId zone) {
		return new OddHoursRule("ODD_HOURS", start, end, new BigDecimal("2000"), 20, zone);
	}

	private static FraudRule velocity(FraudAssessmentRepository repo, Clock clock) {
		return new VelocityRule("VELOCITY", 3, Duration.ofMinutes(10), 35, repo, clock);
	}

	@Test
	void amountThresholdFiresAtThresholdInclusive() {
		var rule = highAmount();
		assertThat(run(rule, "10000", TransactionCategory.RETAIL, "ZA", NOON)).isPresent();
		assertThat(run(rule, "9999.99", TransactionCategory.RETAIL, "ZA", NOON)).isEmpty();
	}

	@Test
	void amountThresholdDescriptionStatesTheCurrency() {
		var hit = run(highAmount(), "12000", TransactionCategory.RETAIL, "ZA", NOON).orElseThrow();

		assertThat(hit.description()).isEqualTo("Amount 12000 ZAR is at or above the 10000 ZAR limit");
	}

	@Test
	void thresholdsApplyToTheConvertedAmountOfAForeignCurrency() {
		// USD 600 at 18.50 is ZAR 11,100: above the ZAR 10,000 threshold even though 600 is a small number.
		var usd = new EvaluationContext(event("600", "USD", TransactionCategory.RETAIL, "US", NOON),
				new BigDecimal("11100.0000"), "ZAR");

		var hit = highAmount().evaluate(usd).orElseThrow();

		assertThat(hit.description()).isEqualTo("Amount 11100 ZAR (600 USD) is at or above the 10000 ZAR limit");
	}

	@Test
	void foreignCurrencyBelowTheThresholdAfterConversionDoesNotFire() {
		var usd = new EvaluationContext(event("500", "USD", TransactionCategory.RETAIL, "US", NOON),
				new BigDecimal("9250.0000"), "ZAR");

		assertThat(highAmount().evaluate(usd)).isEmpty();
	}

	@Test
	void categoryAmountNeedsBothCategoryAndAmount() {
		var rule = riskyCategory();
		assertThat(run(rule, "1000", TransactionCategory.GAMBLING, "ZA", NOON)).isPresent();
		assertThat(run(rule, "999", TransactionCategory.GAMBLING, "ZA", NOON)).isEmpty();
		assertThat(run(rule, "5000", TransactionCategory.GROCERIES, "ZA", NOON)).isEmpty();
	}

	@Test
	void countryListIsCaseInsensitive() {
		var rule = countries("KP");
		assertThat(run(rule, "10", TransactionCategory.RETAIL, "KP", NOON)).isPresent();
		assertThat(run(rule, "10", TransactionCategory.RETAIL, "kp", NOON)).isPresent();
		assertThat(run(rule, "10", TransactionCategory.RETAIL, "ZA", NOON)).isEmpty();
	}

	@Test
	void countryListIgnoresTheLocaleOfTheJvm() {
		Locale original = Locale.getDefault();
		Locale.setDefault(Locale.forLanguageTag("tr-TR"));
		try {
			assertThat(run(countries("ir"), "10", TransactionCategory.RETAIL, "IR", NOON)).isPresent();
		} finally {
			Locale.setDefault(original);
		}
	}

	@Test
	void oddHoursWindowIsStartInclusiveEndExclusive() {
		var rule = oddHours(0, 5, ZoneOffset.UTC);
		assertThat(run(rule, "2000", TransactionCategory.RETAIL, "ZA", OffsetDateTime.parse("2026-03-01T00:00:00Z")))
				.isPresent();
		assertThat(run(rule, "2000", TransactionCategory.RETAIL, "ZA", OffsetDateTime.parse("2026-03-01T04:59:59Z")))
				.isPresent();
		assertThat(run(rule, "2000", TransactionCategory.RETAIL, "ZA", OffsetDateTime.parse("2026-03-01T05:00:00Z")))
				.isEmpty();
		assertThat(run(rule, "1999", TransactionCategory.RETAIL, "ZA", OffsetDateTime.parse("2026-03-01T02:00:00Z")))
				.isEmpty();
	}

	@Test
	void oddHoursWindowIsReadInTheConfiguredZone() {
		// 23:30 UTC is 01:30 in Johannesburg (UTC+2): inside a 00:00-05:00 local window.
		var rule = oddHours(0, 5, ZoneId.of("Africa/Johannesburg"));

		assertThat(run(rule, "2000", TransactionCategory.RETAIL, "ZA", OffsetDateTime.parse("2026-03-01T23:30:00Z")))
				.isPresent();
		assertThat(run(rule, "2000", TransactionCategory.RETAIL, "ZA", OffsetDateTime.parse("2026-03-01T03:30:00Z")))
				.isEmpty();
	}

	@Test
	void oddHoursWindowMayWrapPastMidnight() {
		var rule = new OddHoursRule("ODD_HOURS", 22, 4, new BigDecimal("0.01"), 20, ZoneOffset.UTC);
		assertThat(run(rule, "10", TransactionCategory.RETAIL, "ZA", OffsetDateTime.parse("2026-03-01T23:00:00Z"))).isPresent();
		assertThat(run(rule, "10", TransactionCategory.RETAIL, "ZA", OffsetDateTime.parse("2026-03-01T03:00:00Z"))).isPresent();
		assertThat(run(rule, "10", TransactionCategory.RETAIL, "ZA", NOON)).isEmpty();
	}

	@Test
	void velocityCountsRecentArrivalsPlusCurrent() {
		var repo = mock(FraudAssessmentRepository.class);
		var rule = velocity(repo, Clock.fixed(NOON.toInstant(), ZoneOffset.UTC));
		when(repo.countByAccountIdAndEvaluatedAtBetween("acc1", NOON.minus(Duration.ofMinutes(10)), NOON))
				.thenReturn(2L, 3L);

		assertThat(rule.evaluate(zar("10", TransactionCategory.RETAIL, "ZA", NOON))).isEmpty();
		assertThat(rule.evaluate(zar("10", TransactionCategory.RETAIL, "ZA", NOON))).isPresent();
	}

	@Test
	void velocityIgnoresTheClientsTimestamp() {
		var repo = mock(FraudAssessmentRepository.class);
		var rule = velocity(repo, Clock.fixed(NOON.toInstant(), ZoneOffset.UTC));
		when(repo.countByAccountIdAndEvaluatedAtBetween("acc1", NOON.minus(Duration.ofMinutes(10)), NOON))
				.thenReturn(3L);

		assertThat(rule.evaluate(zar("10", TransactionCategory.RETAIL, "ZA", NOON.minus(Duration.ofDays(1))))).isPresent();
		verify(repo).countByAccountIdAndEvaluatedAtBetween(eq("acc1"), eq(NOON.minus(Duration.ofMinutes(10))),
				eq(NOON));
	}

	@Test
	void activeRuleMarksItsHitsAsShadowOnlyWhenConfiguredThat() {
		var shadow = new ActiveRule("HIGH_AMOUNT", highAmount(), true);

		var hit = shadow.evaluate(zar("12000", TransactionCategory.RETAIL, "ZA", NOON)).orElseThrow();

		assertThat(hit.shadow()).isTrue();
		assertThat(new ActiveRule("HIGH_AMOUNT", highAmount(), false)
				.evaluate(zar("12000", TransactionCategory.RETAIL, "ZA", NOON)).orElseThrow().shadow()).isFalse();
	}
}
