package com.example.capitecproject.repository;

import com.example.capitecproject.domain.FraudAssessment;
import java.time.Duration;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface FraudAssessmentRepository
		extends JpaRepository<FraudAssessment, String>, JpaSpecificationExecutor<FraudAssessment> {

	long countByAccountIdAndTransactionTimeBetween(String accountId, Instant from, Instant to);

	default void lockAccount(String accountId, Duration timeout) {
		setLocalLockTimeout(timeout.toMillis() + "ms");
		acquireAccountLock(accountId);
	}

	@Query(value = "SELECT set_config('lock_timeout', :timeout, true)", nativeQuery = true)
	String setLocalLockTimeout(String timeout);

	@Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext('fraud_assessment.account'), hashtext(:accountId))", nativeQuery = true)
	int acquireAccountLock(String accountId);
}
