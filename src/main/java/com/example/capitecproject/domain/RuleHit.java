package com.example.capitecproject.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * A rule that fired. A shadow hit is recorded for analysis but doesn't count toward the transaction's score or flag.
 */
@Embeddable
public record RuleHit(
		@Column(name = "rule_code", nullable = false) String ruleCode,
		@Column(name = "description", nullable = false) String description,
		@Column(name = "score", nullable = false) int score,
		@Column(name = "shadow", nullable = false) boolean shadow) {

	public RuleHit(String ruleCode, String description, int score) {
		this(ruleCode, description, score, false);
	}

	public RuleHit asShadow(boolean shadow) {
		return new RuleHit(ruleCode, description, score, shadow);
	}
}
