package com.example.capitecproject.service;

import com.example.capitecproject.domain.FraudAssessment;
import com.example.capitecproject.exception.AssessmentNotFoundException;
import com.example.capitecproject.repository.FraudAssessmentRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AssessmentQueryService {

	private final FraudAssessmentRepository repository;

	public FraudAssessment get(String transactionId) {
		return repository.findById(transactionId).orElseThrow(() -> new AssessmentNotFoundException(transactionId));
	}

	public Page<FraudAssessment> search(Boolean flagged, String accountId, OffsetDateTime from, OffsetDateTime to,
			int page, int size) {
		OffsetDateTime fromUtc = toUtc(from);
		OffsetDateTime toBound = toUtc(to);
		Specification<FraudAssessment> spec = Specification.unrestricted();
		if (flagged != null) {
			spec = spec.and((r, q, cb) -> cb.equal(r.get("flagged"), flagged));
		}
		if (accountId != null) {
			spec = spec.and((r, q, cb) -> cb.equal(r.get("accountId"), accountId));
		}
		if (fromUtc != null) {
			spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("transactionTime"), fromUtc));
		}
		if (toBound != null) {
			spec = spec.and((r, q, cb) -> cb.lessThan(r.get("transactionTime"), toBound));
		}
		var pageable = PageRequest.of(page, size,
				Sort.by(Sort.Direction.DESC, "transactionTime").and(Sort.by("transactionId")));
		return repository.findAll(spec, pageable);
	}

	private static OffsetDateTime toUtc(OffsetDateTime value) {
		return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC);
	}
}
