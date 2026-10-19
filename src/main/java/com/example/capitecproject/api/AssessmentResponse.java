package com.example.capitecproject.api;

import com.example.capitecproject.domain.FraudAssessment;
import com.example.capitecproject.domain.RuleHit;
import com.example.capitecproject.domain.TransactionCategory;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AssessmentResponse(
		String transactionId,
		String accountId,
		BigDecimal amount,
		String currency,
        BigDecimal baseAmount,
		BigDecimal rateToBase,
		TransactionCategory category,
		String merchant,
		String country,
		OffsetDateTime timestamp,
		int riskScore,
		boolean flagged,
		List<RuleHit> triggeredRules,
		OffsetDateTime evaluatedAt,
		String ruleSetVersion) {

	static AssessmentResponse from(FraudAssessment a) {
		return new AssessmentResponse(a.getTransactionId(), a.getAccountId(), a.getAmount(), a.getCurrency(),
				a.getBaseAmount(), a.getRateToBase(), a.getCategory(), a.getMerchant(), a.getCountry(),
				a.getTransactionTime(), a.getRiskScore(), a.isFlagged(), List.copyOf(a.getRuleHits()),
				a.getEvaluatedAt(), a.getRuleSetVersion());
	}
}
