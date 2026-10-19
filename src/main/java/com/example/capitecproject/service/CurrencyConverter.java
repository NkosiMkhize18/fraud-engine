package com.example.capitecproject.service;

import com.example.capitecproject.config.FraudProperties;
import com.example.capitecproject.exception.InvalidTransactionException;
import com.example.capitecproject.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Converts a submitted amount to the base currency. A currency is accepted only while it has an exchange rate. */
@Component
@RequiredArgsConstructor
public class CurrencyConverter {

	private static final int BASE_SCALE = 4;

	private final ExchangeRateRepository rates;
	private final FraudProperties properties;

	public record Conversion(BigDecimal baseAmount, BigDecimal rateToBase) {
	}

	public Conversion convert(String currency, BigDecimal amount) {
		checkMinorUnits(currency, amount);
		if (currency.equals(properties.baseCurrency())) {
			return new Conversion(amount.setScale(BASE_SCALE, RoundingMode.UNNECESSARY), BigDecimal.ONE);
		}
		BigDecimal rate = rates.findById(currency)
				.orElseThrow(() -> new InvalidTransactionException("Unsupported currency " + currency))
				.getRateToBase();
		BigDecimal baseAmount = amount.multiply(rate).setScale(BASE_SCALE, RoundingMode.HALF_UP);
		return new Conversion(baseAmount, rate);
	}

	/** Rejects more decimal places than the currency has minor units, e.g. 100.5 JPY (JPY has none). */
	private static void checkMinorUnits(String currency, BigDecimal amount) {
		int digits;
		try {
			digits = Currency.getInstance(currency).getDefaultFractionDigits();
		} catch (IllegalArgumentException ex) {
			return; // not an ISO 4217 code: the exchange-rate lookup decides whether it is accepted
		}
		if (digits >= 0 && amount.stripTrailingZeros().scale() > digits) {
			throw new InvalidTransactionException(
					"Amount has more decimal places than " + currency + " allows (" + digits + ")");
		}
	}
}
