package com.example.capitecproject.rules;

import com.example.capitecproject.domain.RuleHit;
import java.util.Optional;

public interface FraudRule {

	Optional<RuleHit> evaluate(EvaluationContext context);
}
