package com.example.capitecproject.rules;

import com.example.capitecproject.domain.RuleHit;
import java.util.Optional;

/** A rule from the rule file, with its shadow flag. A shadow rule's hits are recorded but don't count toward the score. */
public record ActiveRule(String code, FraudRule rule, boolean shadow) {

	public Optional<RuleHit> evaluate(EvaluationContext context) {
		return rule.evaluate(context).map(hit -> hit.asShadow(shadow));
	}
}
