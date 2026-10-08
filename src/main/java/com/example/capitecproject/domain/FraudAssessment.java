package com.example.capitecproject.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "fraud_assessment")
public class FraudAssessment implements Persistable<String> {

	@Id
	@Column(name = "transaction_id", length = 64)
	private String transactionId;

	@Column(name = "account_id", nullable = false, length = 64)
	private String accountId;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@Column(nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private TransactionCategory category;

	@Column(length = 128)
	private String merchant;

	@Column(nullable = false, length = 2)
	private String country;

	@Column(name = "transaction_time", nullable = false)
	private Instant transactionTime;

	@Column(name = "risk_score", nullable = false)
	private int riskScore;

	@Column(nullable = false)
	private boolean flagged;

	@Column(name = "evaluated_at", nullable = false)
	private Instant evaluatedAt;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "fraud_assessment_rule_hit", joinColumns = @JoinColumn(name = "transaction_id"))
	@Builder.Default
	private List<RuleHit> ruleHits = new ArrayList<>();

	@Transient
	@Builder.Default
	private boolean isNew = true;

	@Override
	public String getId() {
		return transactionId;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		isNew = false;
	}
}
