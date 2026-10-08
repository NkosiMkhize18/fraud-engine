package com.example.capitecproject.rules;

import com.example.capitecproject.domain.RuleHit;
import com.example.capitecproject.domain.TransactionEvent;
import java.util.Optional;

public interface FraudRule {

	Optional<RuleHit> evaluate(TransactionEvent event);
}
