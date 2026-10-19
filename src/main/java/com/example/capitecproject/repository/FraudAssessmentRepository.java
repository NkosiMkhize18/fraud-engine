package com.example.capitecproject.repository;

import com.example.capitecproject.domain.FraudAssessment;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface FraudAssessmentRepository
		extends JpaRepository<FraudAssessment, String>, JpaSpecificationExecutor<FraudAssessment> {

	long countByAccountIdAndEvaluatedAtBetween(String accountId, OffsetDateTime from, OffsetDateTime to);

	default void lockAccount(String accountId, Duration timeout) {
		setLocalLockTimeout(timeout.toMillis() + "ms");
		acquireAccountLock(accountId);
	}

	@Query(value = "SELECT set_config('lock_timeout', :timeout, true)", nativeQuery = true)
	void setLocalLockTimeout(String timeout);

	@Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(:accountId, hashtextextended('fraud_assessment.account', 0)))", nativeQuery = true)
	void acquireAccountLock(String accountId);
}
