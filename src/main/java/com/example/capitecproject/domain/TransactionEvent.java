package com.example.capitecproject.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record TransactionEvent(
		String transactionId,
		String accountId,
		BigDecimal amount,
		String currency,
		TransactionCategory category,
		String merchant,
		String country,
		OffsetDateTime timestamp) {

	public TransactionEvent {
		if (timestamp != null) {
			timestamp = timestamp.withOffsetSameInstant(ZoneOffset.UTC);
		}
	}
}
