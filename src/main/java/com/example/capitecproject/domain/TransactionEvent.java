package com.example.capitecproject.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionEvent(
		String transactionId,
		String accountId,
		BigDecimal amount,
		String currency,
		TransactionCategory category,
		String merchant,
		String country,
		Instant timestamp) {
}
