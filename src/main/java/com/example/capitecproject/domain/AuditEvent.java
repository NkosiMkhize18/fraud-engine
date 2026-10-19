package com.example.capitecproject.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One API action and its outcome. Written once and never changed; the database enforces that (V6). */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "audit_event")
public class AuditEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "occurred_at", nullable = false)
	private OffsetDateTime occurredAt;

	@Column(nullable = false, length = 128)
	private String actor;

	@Column(nullable = false, length = 32)
	private String action;

	@Column(name = "resource_id", length = 64)
	private String resourceId;

	@Column(nullable = false, length = 32)
	private String outcome;

	@Column(name = "request_id", length = 64)
	private String requestId;

	@Column(length = 255)
	private String detail;

	public AuditEvent(OffsetDateTime occurredAt, String actor, String action, String resourceId, String outcome,
			String requestId, String detail) {
		this.occurredAt = occurredAt;
		this.actor = actor;
		this.action = action;
		this.resourceId = resourceId;
		this.outcome = outcome;
		this.requestId = requestId;
		this.detail = detail;
	}
}
