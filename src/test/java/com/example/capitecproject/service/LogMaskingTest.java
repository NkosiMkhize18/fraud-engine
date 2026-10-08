package com.example.capitecproject.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogMaskingTest {

	@Test
	void keepsOnlyTheLastFourCharactersOfAnAccountId() {
		assertThat(FraudEvaluationService.maskAccountId("ACC-1234567890")).isEqualTo("****7890");
	}

	@Test
	void fullyMasksShortOrMissingAccountIds() {
		assertThat(FraudEvaluationService.maskAccountId("1234")).isEqualTo("****");
		assertThat(FraudEvaluationService.maskAccountId("")).isEqualTo("****");
		assertThat(FraudEvaluationService.maskAccountId(null)).isEqualTo("****");
	}
}
