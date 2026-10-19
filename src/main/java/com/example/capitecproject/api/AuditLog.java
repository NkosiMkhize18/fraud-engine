package com.example.capitecproject.api;

import com.example.capitecproject.domain.AuditEvent;
import com.example.capitecproject.repository.AuditEventRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records API activity. Each entry commits in its own transaction, so a rejected or failed request is still recorded
 * even though its own transaction rolled back. If the entry can't be written, the caller gets an error.
 */
@Service
@RequiredArgsConstructor
public class AuditLog {

	private static final int DETAIL_MAX = 255;

	private final AuditEventRepository repository;
	private final Clock clock;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(String actor, String action, String resourceId, String outcome, String detail) {
		repository.save(new AuditEvent(clock.instant().atOffset(ZoneOffset.UTC), actor, action, resourceId, outcome,
				MDC.get(RequestIdFilter.MDC_KEY), truncate(detail)));
	}

	static String truncate(String value) {
		if (value == null || value.length() <= DETAIL_MAX) {
			return value;
		}
		return value.substring(0, DETAIL_MAX);
	}
}
