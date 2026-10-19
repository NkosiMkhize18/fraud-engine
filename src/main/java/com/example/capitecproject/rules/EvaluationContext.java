package com.example.capitecproject.rules;

import com.example.capitecproject.domain.TransactionEvent;
import java.math.BigDecimal;

/**
 * What a rule sees: the submitted event plus its amount converted to the base currency. Rules compare
 * {@code baseAmount} against thresholds, so one threshold applies to every currency.
 */
public record EvaluationContext(TransactionEvent event, BigDecimal baseAmount, String baseCurrency) {

	/** The base-currency amount, with the submitted amount in brackets when the currencies differ. */
	public String amountDescription() {
		String base = money(baseAmount, baseCurrency);
		if (event.currency().equals(baseCurrency)) {
			return base;
		}
		return base + " (" + money(event.amount(), event.currency()) + ")";
	}

	/** Formats an amount in the base currency, e.g. {@code 10000 ZAR}. */
	public String baseMoney(BigDecimal amount) {
		return money(amount, baseCurrency);
	}

	private static String money(BigDecimal amount, String currency) {
		return amount.stripTrailingZeros().toPlainString() + " " + currency;
	}
}
