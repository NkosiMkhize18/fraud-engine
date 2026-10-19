package com.example.capitecproject.rules;

import com.example.capitecproject.domain.RuleHit;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Fires when the converted amount is at or above a minimum and the local hour in {@code zone} falls in the window.
 * The window may wrap past midnight (start 22, end 4).
 */
public class OddHoursRule implements FraudRule {

	private final String code;
	private final int startHour;
	private final int endHour;
	private final BigDecimal minAmount;
	private final int score;
	private final ZoneId zone;

	public OddHoursRule(String code, int startHour, int endHour, BigDecimal minAmount, int score, ZoneId zone) {
		this.code = code;
		this.startHour = startHour;
		this.endHour = endHour;
		this.minAmount = minAmount;
		this.score = score;
		this.zone = zone;
	}

	@Override
	public Optional<RuleHit> evaluate(EvaluationContext context) {
		int hour = context.event().timestamp().atZoneSameInstant(zone).getHour();
		boolean inWindow = startHour <= endHour
				? hour >= startHour && hour < endHour
				: hour >= startHour || hour < endHour;
		if (!inWindow || context.baseAmount().compareTo(minAmount) < 0) {
			return Optional.empty();
		}
		return Optional.of(new RuleHit(code,
				"Transaction of %s at %02d:00 (%s) falls in the %02d:00-%02d:00 quiet window".formatted(
						context.amountDescription(), hour, zone.getId(), startHour, endHour),
				score));
	}
}
