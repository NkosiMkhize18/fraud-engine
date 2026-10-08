package com.example.capitecproject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.capitecproject.domain.FraudAssessment;
import com.example.capitecproject.repository.FraudAssessmentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

class AssessmentQueryServiceTest {

	private final FraudAssessmentRepository repository = mock(FraudAssessmentRepository.class);
	private final AssessmentQueryService service = new AssessmentQueryService(repository);

	@Test
	void getReturnsTheStoredAssessment() {
		var stored = FraudAssessment.builder().transactionId("t1").build();
		when(repository.findById("t1")).thenReturn(Optional.of(stored));

		assertThat(service.get("t1")).isSameAs(stored);
	}

	@Test
	void getUnknownTransactionThrowsNotFound() {
		when(repository.findById("nope")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get("nope")).isInstanceOf(AssessmentNotFoundException.class)
				.hasMessage("No assessment found for transaction nope");
	}

	@Test
	@SuppressWarnings("unchecked")
	void searchPagesNewestFirstWithATieBreakerForStablePages() {
		Page<FraudAssessment> page = new PageImpl<>(List.of());
		when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

		var result = service.search(true, "acc-1", Instant.parse("2026-03-01T00:00:00Z"),
				Instant.parse("2026-03-02T00:00:00Z"), 2, 25);

		assertThat(result).isSameAs(page);
		var pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(repository).findAll(any(Specification.class), pageable.capture());
		assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(25);
		assertThat(pageable.getValue().getSort()).containsExactly(Sort.Order.desc("transactionTime"),
				Sort.Order.asc("transactionId"));
	}
}
