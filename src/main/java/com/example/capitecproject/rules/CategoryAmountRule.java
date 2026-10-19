package com.example.capitecproject.rules;

import com.example.capitecproject.domain.RuleHit;
import com.example.capitecproject.domain.TransactionCategory;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

/** Fires when the category is one of the listed ones and the converted amount is at or above a minimum. */
public class CategoryAmountRule implements FraudRule {

	private final String code;
	private final Set<TransactionCategory> categories;
	private final BigDecimal minAmount;
	private final int score;

	public CategoryAmountRule(String code, Set<TransactionCategory> categories, BigDecimal minAmount, int score) {
		this.code = code;
		this.categories = Set.copyOf(categories);
		this.minAmount = minAmount;
		this.score = score;
	}

	@Override
	public Optional<RuleHit> evaluate(EvaluationContext context) {
		var event = context.event();
		if (!categories.contains(event.category()) || context.baseAmount().compareTo(minAmount) < 0) {
			return Optional.empty();
		}
		return Optional.of(new RuleHit(code,
				"%s transaction of %s is at or above the %s risky-category limit".formatted(event.category(),
						context.amountDescription(), context.baseMoney(minAmount)),
				score));
	}
}
