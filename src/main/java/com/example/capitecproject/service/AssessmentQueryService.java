package com.example.capitecproject.service;

import com.example.capitecproject.domain.FraudAssessment;
import com.example.capitecproject.repository.FraudAssessmentRepository;
import java.time.Instant;
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

	public Page<FraudAssessment> search(Boolean flagged, String accountId, Instant from, Instant to, int page,
			int size) {
		Specification<FraudAssessment> spec = Specification.unrestricted();
		if (flagged != null) {
			spec = spec.and((r, q, cb) -> cb.equal(r.get("flagged"), flagged));
		}
		if (accountId != null) {
			spec = spec.and((r, q, cb) -> cb.equal(r.get("accountId"), accountId));
		}
		if (from != null) {
			spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("transactionTime"), from));
		}
		if (to != null) {
			spec = spec.and((r, q, cb) -> cb.lessThan(r.get("transactionTime"), to));
		}
		var pageable = PageRequest.of(page, size,
				Sort.by(Sort.Direction.DESC, "transactionTime").and(Sort.by("transactionId")));
		return repository.findAll(spec, pageable);
	}
}
