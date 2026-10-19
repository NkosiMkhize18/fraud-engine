package com.example.capitecproject.service;

import com.example.capitecproject.config.FraudProperties;
import com.example.capitecproject.domain.FraudAssessment;
import com.example.capitecproject.domain.RuleHit;
import com.example.capitecproject.domain.TransactionEvent;
import com.example.capitecproject.exception.AccountBusyException;
import com.example.capitecproject.exception.DuplicateTransactionException;
import com.example.capitecproject.exception.InvalidTransactionException;
import com.example.capitecproject.repository.FraudAssessmentRepository;
import com.example.capitecproject.rules.EvaluationContext;
import com.example.capitecproject.rules.RuleSet;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudEvaluationService {

	public record Result(FraudAssessment assessment, boolean created) {
	}

	private final RuleSet ruleSet;
	private final FraudAssessmentRepository repository;
	private final FraudProperties properties;
	private final CurrencyConverter converter;
	private final Clock clock;
	private final TransactionTemplate transactionTemplate;
	private final FraudMetrics metrics;

	public Result evaluate(TransactionEvent event) {
		Result result;
		Optional<FraudAssessment> stored = repository.findById(event.transactionId());
		if (stored.isPresent()) {
			result = replay(stored.get(), event);
		} else {
			checkTimestamp(event);
			CurrencyConverter.Conversion conversion = converter.convert(event.currency(), event.amount());
			try {
				result = metrics.timeEvaluation(
						() -> transactionTemplate.execute(status -> evaluateUnderAccountLock(event, conversion)));
			} catch (DataIntegrityViolationException ex) {
				// Another request stored this transaction id between our lookup and our insert.
				FraudAssessment winner = repository.findById(event.transactionId()).orElseThrow(() -> ex);
				result = replay(winner, event);
			}
		}
		metrics.recordResult(result);
		return result;
	}

	private Result evaluateUnderAccountLock(TransactionEvent event, CurrencyConverter.Conversion conversion) {
		try {
			repository.lockAccount(event.accountId(), properties.accountLockTimeout());
		} catch (PessimisticLockingFailureException ex) {
			log.atWarn()
					.addKeyValue("transactionId", event.transactionId())
					.addKeyValue("accountId", maskAccountId(event.accountId()))
					.log("Account busy: too many concurrent requests");
			metrics.recordAccountBusy();
			throw new AccountBusyException(ex);
		}

		Optional<FraudAssessment> existing = repository.findById(event.transactionId());
		if (existing.isPresent()) {
			return replay(existing.get(), event);
		}

		var context = new EvaluationContext(event, conversion.baseAmount(), properties.baseCurrency());
		List<RuleHit> hits = ruleSet.rules().stream().map(rule -> rule.evaluate(context)).flatMap(Optional::stream).toList();

		// Shadow hits are kept for analysis but don't count toward the score or the flag.
		int score = hits.stream().filter(hit -> !hit.shadow()).mapToInt(RuleHit::score).sum();

		boolean flagged = score >= properties.flagThreshold();
		var assessment = FraudAssessment.builder()
				.transactionId(event.transactionId())
				.accountId(event.accountId())
				.amount(event.amount().setScale(4, RoundingMode.UNNECESSARY))
				.currency(event.currency())
				.baseAmount(conversion.baseAmount())
				.rateToBase(conversion.rateToBase())
				.ruleSetVersion(ruleSet.version())
				.category(event.category())
				.merchant(event.merchant())
				.country(event.country())
				.transactionTime(event.timestamp().truncatedTo(ChronoUnit.MICROS))
				.ruleHits(new ArrayList<>(hits))
				.riskScore(score)
				.flagged(flagged)
				.evaluatedAt(now().truncatedTo(ChronoUnit.MICROS))
				.build();

		repository.saveAndFlush(assessment);
		log.atInfo()
				.addKeyValue("transactionId", event.transactionId())
				.addKeyValue("accountId", maskAccountId(event.accountId()))
				.addKeyValue("currency", event.currency())
				.addKeyValue("ruleSetVersion", ruleSet.version())
				.addKeyValue("riskScore", score)
				.addKeyValue("flagged", flagged)
				.addKeyValue("rules", hits.stream().map(RuleHit::ruleCode).toList())
				.log("Transaction assessed");
		return new Result(assessment, true);
	}

	private Result replay(FraudAssessment stored, TransactionEvent event) {
		return new Result(verifyReplay(stored, event), false);
	}

	private FraudAssessment verifyReplay(FraudAssessment stored, TransactionEvent event) {
		if (!stored.matches(event)) {
			metrics.recordConflict();
			log.atWarn()
					.addKeyValue("transactionId", event.transactionId())
					.log("Transaction id reused with different details");
			throw new DuplicateTransactionException(event.transactionId());
		}
		return stored;
	}

	private void checkTimestamp(TransactionEvent event) {
		OffsetDateTime now = now();
		var timestamps = properties.timestamp();
		if (event.timestamp().isBefore(now.minus(timestamps.maxAge()))) {
			throw new InvalidTransactionException("Timestamp is more than " + describe(timestamps.maxAge()) + " in the past");
		}
		if (event.timestamp().isAfter(now.plus(timestamps.maxFutureSkew()))) {
			throw new InvalidTransactionException(
					"Timestamp is more than " + describe(timestamps.maxFutureSkew()) + " in the future");
		}
	}

	private OffsetDateTime now() {
		return clock.instant().atOffset(ZoneOffset.UTC);
	}

	/** e.g. PT24H becomes "24 hours" and PT5M becomes "5 minutes". */
	static String describe(Duration duration) {
		if (duration.toHours() > 0 && duration.toMinutes() % 60 == 0) {
			long hours = duration.toHours();
			return hours + (hours == 1 ? " hour" : " hours");
		}
		long minutes = duration.toMinutes();
		return minutes + (minutes == 1 ? " minute" : " minutes");
	}

	static String maskAccountId(String accountId) {
		if (accountId == null || accountId.length() <= 4) {
			return "****";
		}
		return "****" + accountId.substring(accountId.length() - 4);
	}
}
