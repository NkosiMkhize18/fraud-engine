package com.example.capitecproject.service;

import com.example.capitecproject.config.FraudProperties;
import com.example.capitecproject.domain.FraudAssessment;
import com.example.capitecproject.domain.RuleHit;
import com.example.capitecproject.domain.TransactionEvent;
import com.example.capitecproject.repository.FraudAssessmentRepository;
import com.example.capitecproject.rules.FraudRule;
import java.math.RoundingMode;
import java.time.Clock;
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

	private final List<FraudRule> rules;
	private final FraudAssessmentRepository repository;
	private final FraudProperties properties;
	private final Clock clock;
	private final TransactionTemplate transactionTemplate;
	private final FraudMetrics metrics;

	public Result evaluate(TransactionEvent event) {
		Result result;
		try {
			result = metrics.timeEvaluation(
					() -> transactionTemplate.execute(status -> evaluateUnderAccountLock(event)));
		} catch (DataIntegrityViolationException ex) {
			result = new Result(repository.findById(event.transactionId()).orElseThrow(() -> ex), false);
		}
		metrics.recordResult(result);
		return result;
	}

	private Result evaluateUnderAccountLock(TransactionEvent event) {
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
			return new Result(existing.get(), false);
		}

		List<RuleHit> hits = rules.stream().map(rule -> rule.evaluate(event)).flatMap(hit -> hit.stream()).toList();
		int score = hits.stream().mapToInt(hit -> hit.score()).sum();
		boolean flagged = score >= properties.flagThreshold();
		var assessment = FraudAssessment.builder()
				.transactionId(event.transactionId())
				.accountId(event.accountId())
				.amount(event.amount().setScale(4, RoundingMode.UNNECESSARY))
				.currency(event.currency())
				.category(event.category())
				.merchant(event.merchant())
				.country(event.country())
				.transactionTime(event.timestamp().truncatedTo(ChronoUnit.MICROS))
				.ruleHits(new ArrayList<>(hits))
				.riskScore(score)
				.flagged(flagged)
				.evaluatedAt(clock.instant().truncatedTo(ChronoUnit.MICROS))
				.build();

		repository.saveAndFlush(assessment);
		log.atInfo()
				.addKeyValue("transactionId", event.transactionId())
				.addKeyValue("accountId", maskAccountId(event.accountId()))
				.addKeyValue("riskScore", score)
				.addKeyValue("flagged", flagged)
				.addKeyValue("rules", hits.stream().map(hit -> hit.ruleCode()).toList())
				.log("Transaction assessed");
		return new Result(assessment, true);
	}

	static String maskAccountId(String accountId) {
		if (accountId == null || accountId.length() <= 4) {
			return "****";
		}
		return "****" + accountId.substring(accountId.length() - 4);
	}
}
