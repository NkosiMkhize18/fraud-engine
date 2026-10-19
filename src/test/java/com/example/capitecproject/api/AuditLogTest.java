package com.example.capitecproject.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.capitecproject.domain.AuditEvent;
import com.example.capitecproject.repository.AuditEventRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

class AuditLogTest {

	private final AuditEventRepository repository = mock(AuditEventRepository.class);
	private final Clock clock = Clock.fixed(OffsetDateTime.parse("2026-03-01T12:00:00Z").toInstant(), ZoneOffset.UTC);
	private final AuditLog audit = new AuditLog(repository, clock);

	@AfterEach
	void clearLoggingContext() {
		MDC.clear();
	}

	private AuditEvent recorded() {
		var captor = ArgumentCaptor.forClass(AuditEvent.class);
		verify(repository).save(captor.capture());
		return captor.getValue();
	}

	@Test
	void recordsWhoWhatWhenAndTheRequestId() {
		MDC.put(RequestIdFilter.MDC_KEY, "req-123");

		audit.record("transaction-submitter", "SUBMIT", "t1", "CREATED", "score=70");

		var event = recorded();
		assertThat(event.getActor()).isEqualTo("transaction-submitter");
		assertThat(event.getAction()).isEqualTo("SUBMIT");
		assertThat(event.getResourceId()).isEqualTo("t1");
		assertThat(event.getOutcome()).isEqualTo("CREATED");
		assertThat(event.getDetail()).isEqualTo("score=70");
		assertThat(event.getRequestId()).isEqualTo("req-123");
		assertThat(event.getOccurredAt()).isEqualTo(OffsetDateTime.parse("2026-03-01T12:00:00Z"));
	}

	@Test
	void requestIdIsNullOutsideARequest() {
		audit.record("assessment-reader", "READ", "t1", "OK", null);

		assertThat(recorded().getRequestId()).isNull();
	}

	@Test
	void longDetailIsTruncatedToTheColumnSize() {
		audit.record("assessment-reader", "SEARCH", null, "OK", "x".repeat(400));

		assertThat(recorded().getDetail()).hasSize(255);
	}
}
