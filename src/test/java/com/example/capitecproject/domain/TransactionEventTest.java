package com.example.capitecproject.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TransactionEventTest {

	@Test
	void timestampIsNormalizedToUtc() {
		var local = OffsetDateTime.parse("2026-03-01T14:00:00+02:00");

		var event = new TransactionEvent("txn-1", "acc-1", BigDecimal.TEN, "ZAR", TransactionCategory.RETAIL,
				"Shop", "ZA", local);

		assertThat(event.timestamp().getOffset()).isEqualTo(ZoneOffset.UTC);
		assertThat(event.timestamp().toInstant()).isEqualTo(local.toInstant());
		assertThat(event.timestamp().getHour()).isEqualTo(12);
	}
}
