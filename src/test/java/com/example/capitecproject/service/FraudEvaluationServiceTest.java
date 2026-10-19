package com.example.capitecproject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.capitecproject.config.FraudProperties;
import com.example.capitecproject.domain.ExchangeRate;
import com.example.capitecproject.domain.FraudAssessment;
import com.example.capitecproject.rules.EvaluationContext;
import com.example.capitecproject.domain.RuleHit;
import com.example.capitecproject.domain.TransactionCategory;
import com.example.capitecproject.domain.TransactionEvent;
import com.example.capitecproject.exception.AccountBusyException;
import com.example.capitecproject.exception.DuplicateTransactionException;
import com.example.capitecproject.exception.InvalidTransactionException;
import com.example.capitecproject.repository.ExchangeRateRepository;
import com.example.capitecproject.repository.FraudAssessmentRepository;
import com.example.capitecproject.rules.ActiveRule;
import com.example.capitecproject.rules.FraudRule;
import com.example.capitecproject.rules.RuleSet;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class FraudEvaluationServiceTest {

	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-03-01T12:00:05.123456789Z");
	private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(1);

	private final FraudAssessmentRepository repository = mock(FraudAssessmentRepository.class);
	private final ExchangeRateRepository rates = mock(ExchangeRateRepository.class);
	private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
	private final FraudProperties properties = new FraudProperties(50, LOCK_TIMEOUT, "ZAR", null,
			new FraudProperties.Timestamps(Duration.ofHours(24), Duration.ofMinutes(5)));

	private FraudEvaluationService service(FraudRule... rules) {
		return serviceWith(java.util.Arrays.stream(rules).map(rule -> new ActiveRule("RULE", rule, false)).toList());
	}

	private FraudEvaluationService serviceWith(List<ActiveRule> active) {
		return new FraudEvaluationService(new RuleSet(active, "test-version"), repository, properties,
				new CurrencyConverter(rates, properties), Clock.fixed(NOW.toInstant(), ZoneOffset.UTC),
				new TransactionTemplate(transactionManager), new FraudMetrics(meterRegistry));
	}

	private static FraudRule hit(String code, int score) {
		return context -> Optional.of(new RuleHit(code, code + " triggered", score));
	}

	private static FraudRule noHit() {
		return context -> Optional.empty();
	}

	private static TransactionEvent event(String id) {
		return new TransactionEvent(id, "acc-1", new BigDecimal("15000.5"), "ZAR", TransactionCategory.GAMBLING, "Acme",
				"ZA", OffsetDateTime.parse("2026-03-01T12:00:00.123456789Z"));
	}

	private static FraudAssessment matchingStored(String id) {
		return FraudAssessment.builder().transactionId(id).accountId("acc-1").amount(new BigDecimal("15000.5000"))
				.currency("ZAR").baseAmount(new BigDecimal("15000.5000")).rateToBase(BigDecimal.ONE)
				.category(TransactionCategory.GAMBLING).merchant("Acme").country("ZA")
				.transactionTime(OffsetDateTime.parse("2026-03-01T12:00:00.123456Z")).riskScore(70).flagged(true)
				.evaluatedAt(NOW).build();
	}

	private double count(String name, String... tags) {
		return meterRegistry.get(name).tags(tags).counter().count();
	}

	@BeforeEach
	void noStoredAssessments() {
		when(repository.findById(any())).thenReturn(Optional.empty());
	}

	@Test
	void newTransactionIsScoredAgainstEveryRuleAndSaved() {
		var result = service(hit("HIGH_AMOUNT", 40), noHit(), hit("RISKY_CATEGORY", 30)).evaluate(event("t1"));

		assertThat(result.created()).isTrue();
		var captor = ArgumentCaptor.forClass(FraudAssessment.class);
		verify(repository).saveAndFlush(captor.capture());
		FraudAssessment saved = captor.getValue();
		assertThat(saved).isSameAs(result.assessment());
		assertThat(saved.getRiskScore()).isEqualTo(70);
		assertThat(saved.isFlagged()).isTrue();
		assertThat(saved.getRuleHits()).extracting(hit -> hit.ruleCode()).containsExactly("HIGH_AMOUNT", "RISKY_CATEGORY");
		assertThat(saved.getAccountId()).isEqualTo("acc-1");
		assertThat(saved.getAmount()).isEqualByComparingTo("15000.5").hasScaleOf(4);
		assertThat(saved.getBaseAmount()).isEqualByComparingTo("15000.5").hasScaleOf(4);
		assertThat(saved.getRateToBase()).isEqualByComparingTo("1");
		assertThat(saved.getTransactionTime()).isEqualTo(OffsetDateTime.parse("2026-03-01T12:00:00.123456Z"));
		assertThat(saved.getEvaluatedAt()).isEqualTo(OffsetDateTime.parse("2026-03-01T12:00:05.123456Z"));
		verify(transactionManager).commit(any());
	}

	@Test
	void foreignCurrencyIsConvertedToTheBaseCurrencyAndStoredWithTheRateUsed() {
		when(rates.findById("USD")).thenReturn(Optional.of(rate("USD", "18.5")));
		var usd = new TransactionEvent("t1", "acc-1", new BigDecimal("600"), "USD", TransactionCategory.RETAIL, null,
				"US", NOW);

		var result = service(hit("A", 10)).evaluate(usd);

		assertThat(result.assessment().getCurrency()).isEqualTo("USD");
		assertThat(result.assessment().getAmount()).isEqualByComparingTo("600");
		assertThat(result.assessment().getBaseAmount()).isEqualByComparingTo("11100");
		assertThat(result.assessment().getRateToBase()).isEqualByComparingTo("18.5");
	}

	@Test
	void rulesReceiveTheBaseCurrencyAmount() {
		when(rates.findById("USD")).thenReturn(Optional.of(rate("USD", "18.5")));
		FraudRule rule = mock(FraudRule.class);
		when(rule.evaluate(any())).thenReturn(Optional.empty());
		var usd = new TransactionEvent("t1", "acc-1", new BigDecimal("600"), "USD", TransactionCategory.RETAIL, null,
				"US", NOW);

		service(rule).evaluate(usd);

		var captor = ArgumentCaptor.forClass(EvaluationContext.class);
		verify(rule).evaluate(captor.capture());
		assertThat(captor.getValue().baseAmount()).isEqualByComparingTo("11100");
		assertThat(captor.getValue().baseCurrency()).isEqualTo("ZAR");
	}

	@Test
	void scoreBelowThresholdIsRecordedButNotFlagged() {
		var result = service(hit("HIGH_RISK_COUNTRY", 30)).evaluate(event("t1"));

		assertThat(result.assessment().getRiskScore()).isEqualTo(30);
		assertThat(result.assessment().isFlagged()).isFalse();
	}

	@Test
	void scoreEqualToThresholdIsFlagged() {
		assertThat(service(hit("A", 25), hit("B", 25)).evaluate(event("t1")).assessment().isFlagged()).isTrue();
	}

	@Test
	void accountIsLockedBeforeLookingUpOrScoring() {
		service(hit("A", 10)).evaluate(event("t1"));

		var order = inOrder(repository);
		order.verify(repository).findById("t1");
		order.verify(repository).lockAccount("acc-1", LOCK_TIMEOUT);
		order.verify(repository).findById("t1");
		order.verify(repository).saveAndFlush(any());
	}

	@Test
	void identicalReplayReturnsTheStoredAssessmentWithoutRescoring() {
		var stored = matchingStored("t1");
		when(repository.findById("t1")).thenReturn(Optional.of(stored));
		FraudRule rule = mock(FraudRule.class);

		var result = service(rule).evaluate(event("t1"));

		assertThat(result.created()).isFalse();
		assertThat(result.assessment()).isSameAs(stored);
		verifyNoInteractions(rule);
		verify(repository, never()).saveAndFlush(any());
		verifyNoInteractions(transactionManager);
		assertThat(count("fraud.submissions", "outcome", "duplicate")).isEqualTo(1);
	}

	@Test
	void replayOfAnOlderStoredTransactionIsAnsweredEvenAfterTheTimestampWindowHasPassed() {
		// Stored when it was new; the client retries two days later with the same details.
		var stored = FraudAssessment.builder().transactionId("t1").accountId("acc-1").amount(new BigDecimal("15000.5000"))
				.currency("ZAR").baseAmount(new BigDecimal("15000.5000")).rateToBase(BigDecimal.ONE)
				.category(TransactionCategory.GAMBLING).merchant("Acme").country("ZA")
				.transactionTime(NOW.minus(Duration.ofDays(2)).truncatedTo(java.time.temporal.ChronoUnit.MICROS))
				.riskScore(70).flagged(true).evaluatedAt(NOW.minus(Duration.ofDays(2))).build();
		when(repository.findById("t1")).thenReturn(Optional.of(stored));
		var retry = new TransactionEvent("t1", "acc-1", new BigDecimal("15000.5"), "ZAR", TransactionCategory.GAMBLING,
				"Acme", "ZA", NOW.minus(Duration.ofDays(2)));

		var result = service(hit("A", 10)).evaluate(retry);

		assertThat(result.created()).isFalse();
		assertThat(result.assessment()).isSameAs(stored);
	}

	@Test
	void replayWithDifferentDetailsIsRejectedAsAConflict() {
		var stored = matchingStored("t1");
		when(repository.findById("t1")).thenReturn(Optional.of(stored));
		var changed = new TransactionEvent("t1", "acc-1", new BigDecimal("1"), "ZAR", TransactionCategory.GAMBLING,
				"Acme", "ZA", OffsetDateTime.parse("2026-03-01T12:00:00.123456789Z"));

		assertThatThrownBy(() -> service(hit("A", 10)).evaluate(changed))
				.isInstanceOf(DuplicateTransactionException.class);

		verify(repository, never()).saveAndFlush(any());
		assertThat(count("fraud.submissions", "outcome", "conflict")).isEqualTo(1);
	}

	@Test
	void sameIdFromAnotherAccountIsRejectedAsAConflict() {
		var stored = FraudAssessment.builder().transactionId("t1").accountId("acc-2").amount(new BigDecimal("15000.5000"))
				.currency("ZAR").category(TransactionCategory.GAMBLING).merchant("Acme").country("ZA")
				.transactionTime(OffsetDateTime.parse("2026-03-01T12:00:00.123456Z")).build();
		when(repository.findById("t1")).thenReturn(Optional.of(stored));

		assertThatThrownBy(() -> service(hit("A", 10)).evaluate(event("t1")))
				.isInstanceOf(DuplicateTransactionException.class);
	}

	@Test
	void lockTimeoutRejectsTheRequestAsAccountBusyAndRollsBack() {
		doThrow(new CannotAcquireLockException("lock timeout")).when(repository).lockAccount("acc-1", LOCK_TIMEOUT);

		assertThatThrownBy(() -> service(hit("A", 10)).evaluate(event("t1")))
				.isInstanceOf(AccountBusyException.class);

		verify(repository, never()).saveAndFlush(any());
		verify(transactionManager).rollback(any());
		assertThat(count("fraud.submissions", "outcome", "account_busy")).isEqualTo(1);
	}

	@Test
	void concurrentInsertOfTheSameIdWithSameDetailsReturnsTheStoredOne() {
		var stored = matchingStored("t1");
		// lookup before the lock, lookup under the lock (both empty), then the lookup after the insert conflict
		when(repository.findById("t1")).thenReturn(Optional.empty(), Optional.empty(), Optional.of(stored));
		when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

		var result = service(hit("A", 10)).evaluate(event("t1"));

		assertThat(result.created()).isFalse();
		assertThat(result.assessment()).isSameAs(stored);
		verify(transactionManager).rollback(any());
	}

	@Test
	void integrityViolationIsRethrownWhenNoStoredAssessmentExplainsIt() {
		when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("not null"));

		assertThatThrownBy(() -> service(hit("A", 10)).evaluate(event("t1")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void timestampOlderThanTheWindowIsRejectedBeforeAnyWrite() {
		var old = new TransactionEvent("t1", "acc-1", BigDecimal.TEN, "ZAR", TransactionCategory.RETAIL, null, "ZA",
				NOW.minus(Duration.ofHours(25)));

		assertThatThrownBy(() -> service(hit("A", 10)).evaluate(old)).isInstanceOf(InvalidTransactionException.class);
		verifyNoInteractions(transactionManager);
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void timestampAheadOfTheClockBeyondTheSkewIsRejected() {
		var future = new TransactionEvent("t1", "acc-1", BigDecimal.TEN, "ZAR", TransactionCategory.RETAIL, null, "ZA",
				NOW.plus(Duration.ofMinutes(6)));

		assertThatThrownBy(() -> service(hit("A", 10)).evaluate(future)).isInstanceOf(InvalidTransactionException.class);
		verifyNoInteractions(transactionManager);
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void aCurrencyWithoutAnExchangeRateIsRejectedBeforeTheLock() {
		when(rates.findById("XYZ")).thenReturn(Optional.empty());
		var unknown = new TransactionEvent("t1", "acc-1", BigDecimal.TEN, "XYZ", TransactionCategory.RETAIL, null,
				"ZA", NOW);

		assertThatThrownBy(() -> service(hit("A", 10)).evaluate(unknown))
				.isInstanceOf(InvalidTransactionException.class)
				.hasMessage("Unsupported currency XYZ");
		verifyNoInteractions(transactionManager);
		verify(repository, never()).lockAccount(any(), any());
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void newAssessmentsAreCounted() {
		service(hit("HIGH_AMOUNT", 40), hit("RISKY_CATEGORY", 30)).evaluate(event("t1"));

		assertThat(count("fraud.submissions", "outcome", "assessed")).isEqualTo(1);
		assertThat(count("fraud.assessments", "flagged", "true")).isEqualTo(1);
		assertThat(count("fraud.rule.hits", "rule", "HIGH_AMOUNT")).isEqualTo(1);
		assertThat(meterRegistry.get("fraud.evaluation").timer().count()).isEqualTo(1);
	}

	@Test
	void shadowHitsAreRecordedButDoNotCountTowardTheScoreOrFlag() {
		var result = serviceWith(List.of(
				new ActiveRule("ENFORCED", hit("ENFORCED", 30), false),
				new ActiveRule("NEW_RULE", hit("NEW_RULE", 40), true)))
				.evaluate(event("t1"));

		assertThat(result.assessment().getRuleHits()).extracting(RuleHit::ruleCode, RuleHit::shadow)
				.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("ENFORCED", false),
						org.assertj.core.groups.Tuple.tuple("NEW_RULE", true));
		assertThat(result.assessment().getRiskScore()).isEqualTo(30);
		assertThat(result.assessment().isFlagged()).isFalse();
	}

	@Test
	void newAssessmentRecordsTheRuleSetVersionThatScoredIt() {
		var result = service(hit("A", 10)).evaluate(event("t1"));

		assertThat(result.assessment().getRuleSetVersion()).isEqualTo("test-version");
	}

	@Test
	void timestampWindowsAreDescribedInWords() {
		assertThat(FraudEvaluationService.describe(Duration.ofHours(24))).isEqualTo("24 hours");
		assertThat(FraudEvaluationService.describe(Duration.ofHours(1))).isEqualTo("1 hour");
		assertThat(FraudEvaluationService.describe(Duration.ofMinutes(5))).isEqualTo("5 minutes");
		assertThat(FraudEvaluationService.describe(Duration.ofMinutes(1))).isEqualTo("1 minute");
	}

	private static ExchangeRate rate(String currency, String rateToBase) {
		return new ExchangeRate(currency, new BigDecimal(rateToBase));
	}
}
