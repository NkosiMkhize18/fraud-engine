package com.example.capitecproject.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record RuleHit(
		@Column(name = "rule_code", nullable = false) String ruleCode,
		@Column(name = "description", nullable = false) String description,
		@Column(name = "score", nullable = false) int score) {
}
