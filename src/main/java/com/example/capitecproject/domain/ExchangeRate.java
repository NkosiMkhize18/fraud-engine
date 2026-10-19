package com.example.capitecproject.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A currency the service accepts, and its rate to the base currency. Read-only here: rows are maintained in SQL. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "exchange_rate")
public class ExchangeRate {

	@Id
	@Column(length = 3)
	private String currency;

	@Column(name = "rate_to_base", nullable = false, precision = 19, scale = 8)
	private BigDecimal rateToBase;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime updatedAt;

	public ExchangeRate(String currency, BigDecimal rateToBase) {
		this.currency = currency;
		this.rateToBase = rateToBase;
	}
}
