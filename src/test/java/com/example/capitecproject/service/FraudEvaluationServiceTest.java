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
import com.example.capitecproject.domain.FraudAssessment;
import com.example.capitecproject.domain.RuleHit;
import com.example.capitecproject.domain.TransactionCategory;
import com.example.capitecproject.domain.TransactionEvent;
import com.example.capitecproject.repository.FraudAssessmentRepository;
import com.example.capitecproject.rules.FraudRule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

	private static final Instant NOW = Instant.parse("2026-03-01T12:00:05.123456789Z");
	private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(1);

	private final FraudAssessmentRepository repository = mock(FraudAssessmentRepository.class);
	private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

	private FraudEvaluationService service(FraudRule... rules) {
		return new FraudEvaluationService(List.of(rules), repository,
				new FraudProperties(50, LOCK_TIMEOUT, null), Clock.fixed(NOW, ZoneOffset.UTC),
				new TransactionTemplate(transactionManager), new FraudMetrics(meterRegistry));
	}

	private static FraudRule hit(String code, int score) {
		return event -> Optional.of(new RuleHit(code, code + " triggered", score));
	}

	private static FraudRule noHit() {
		return event -> Optional.empty();
	}

	private static TransactionEvent event(String id) {
		return new TransactionEvent(id, "acc-1", new BigDecimal("15000.5"), "ZAR", TransactionCategory.GAMBLING, "Acme",
				"ZA", Instant.parse("2026-03-01T12:00:00.123456789Z"));
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
		assertThat(saved.getTransactionTime()).isEqualTo(Instant.parse("2026-03-01T12:00:00.123456Z"));
		assertThat(saved.getEvaluatedAt()).isEqualTo(Instant.parse("2026-03-01T12:00:05.123456Z"));
		verify(transactionManager).commit(any());
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
		order.verify(repository).lockAccount("acc-1", LOCK_TIMEOUT);
		order.verify(repository).findById("t1");
		order.verify(repository).saveAndFlush(any());
	}

	@Test
	void alreadyAssessedTransactionIsReturnedWithoutRescoring() {
		var stored = FraudAssessment.builder().transactionId("t1").accountId("acc-1").riskScore(70).flagged(true)
				.build();
		when(repository.findById("t1")).thenReturn(Optional.of(stored));
		FraudRule rule = mock(FraudRule.class);

		var result = service(rule).evaluate(event("t1"));

		assertThat(result.created()).isFalse();
		assertThat(result.assessment()).isSameAs(stored);
		verifyNoInteractions(rule);
		verify(repository, never()).saveAndFlush(any());
		assertThat(count("fraud.submissions", "outcome", "duplicate")).isEqualTo(1);
	}

	@Test
	void lockTimeoutRejectsTheRequestAsAccountBusyAndRollsBack() {
		doThrow(new CannotAcquireLockException("lock timeout")).when(repository).lockAccount("acc-1", LOCK_TIMEOUT);

		assertThatThrownBy(() -> service(hit("A", 10)).evaluate(event("t1")))
				.isInstanceOf(AccountBusyException.class);

		verify(repository, never()).findById(any());
		verify(repository, never()).saveAndFlush(any());
		verify(transactionManager).rollback(any());
		assertThat(count("fraud.submissions", "outcome", "account_busy")).isEqualTo(1);
	}

	@Test
	void sameTransactionIdStoredConcurrentlyByAnotherAccountReturnsTheStoredOne() {
		var stored = FraudAssessment.builder().transactionId("t1").accountId("acc-2").build();
		when(repository.findById("t1")).thenReturn(Optional.empty()).thenReturn(Optional.of(stored));
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
	void newAssessmentsAreCounted() {
		service(hit("HIGH_AMOUNT", 40), hit("RISKY_CATEGORY", 30)).evaluate(event("t1"));

		assertThat(count("fraud.submissions", "outcome", "assessed")).isEqualTo(1);
		assertThat(count("fraud.assessments", "flagged", "true")).isEqualTo(1);
		assertThat(count("fraud.rule.hits", "rule", "HIGH_AMOUNT")).isEqualTo(1);
		assertThat(meterRegistry.get("fraud.evaluation").timer().count()).isEqualTo(1);
	}
}
