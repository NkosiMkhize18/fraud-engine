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
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
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
	private OffsetDateTime transactionTime;

	@Column(name = "risk_score", nullable = false)
	private int riskScore;

	@Column(nullable = false)
	private boolean flagged;

	@Column(name = "evaluated_at", nullable = false)
	private OffsetDateTime evaluatedAt;

	/** The amount converted to the base currency (fraud.base-currency) that the rules were scored against. */
	@Column(name = "base_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal baseAmount;

	/** The rate used for that conversion, kept so the assessment can be audited after rates change. */
	@Column(name = "rate_to_base", nullable = false, precision = 19, scale = 8)
	private BigDecimal rateToBase;

	/** The version of the rule file that scored this assessment (see RuleSet). Null for assessments made before V5. */
	@Column(name = "rule_set_version", length = 64)
	private String ruleSetVersion;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "fraud_assessment_rule_hit", joinColumns = @JoinColumn(name = "transaction_id"))
	@BatchSize(size = 100)
	@Builder.Default
	private List<RuleHit> ruleHits = new ArrayList<>();

	@Transient
	@Builder.Default
	private boolean isNew = true;

	@Override
	public String getId() {
		return transactionId;
	}

	public boolean matches(TransactionEvent event) {
		return accountId.equals(event.accountId())
				&& amount.compareTo(event.amount()) == 0
				&& currency.equals(event.currency())
				&& category == event.category()
				&& Objects.equals(merchant, event.merchant())
				&& country.equals(event.country())
				&& transactionTime.isEqual(event.timestamp().truncatedTo(ChronoUnit.MICROS));
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		isNew = false;
	}
}
