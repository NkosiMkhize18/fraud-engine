package com.example.capitecproject.rules;

import com.example.capitecproject.config.FraudProperties;
import com.example.capitecproject.domain.RuleHit;
import com.example.capitecproject.domain.TransactionEvent;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fraud.rules.odd-hours.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OddHoursRule implements FraudRule {

	public static final String CODE = "ODD_HOURS";

	private final FraudProperties properties;

	@Override
	public Optional<RuleHit> evaluate(TransactionEvent event) {
		var config = properties.rules().oddHours();
		int hour = event.timestamp().atOffset(ZoneOffset.UTC).getHour();
		boolean inWindow = config.startHour() <= config.endHour()
				? hour >= config.startHour() && hour < config.endHour()
				: hour >= config.startHour() || hour < config.endHour();
		if (!inWindow || event.amount().compareTo(config.minAmount()) < 0) {
			return Optional.empty();
		}
		return Optional.of(new RuleHit(CODE,
				"Transaction of %s at %02d:00 UTC falls in the %02d:00-%02d:00 quiet window".formatted(
						event.amount().toPlainString(), hour, config.startHour(), config.endHour()),
				config.score()));
	}
}
