package com.example.capitecproject.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.capitecproject.domain.TransactionCategory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TransactionRequestValidationTest {

	private static jakarta.validation.ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void createValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	private static TransactionRequest request(String amount, String currency, String country) {
		return new TransactionRequest("t1", "acc-1", amount == null ? null : new BigDecimal(amount), currency,
				TransactionCategory.RETAIL, "Acme", country, OffsetDateTime.parse("2026-03-01T12:00:00Z"));
	}

	private static Set<String> invalidFields(TransactionRequest request) {
		return validator.validate(request).stream().map(ConstraintViolation::getPropertyPath).map(Object::toString)
				.collect(Collectors.toSet());
	}

	@Test
	void validRequestPasses() {
		assertThat(invalidFields(request("150.50", "ZAR", "ZA"))).isEmpty();
	}

	@Test
	void amountMustBePositiveWithAtMostFourDecimals() {
		assertThat(invalidFields(request("0", "ZAR", "ZA"))).containsExactly("amount");
		assertThat(invalidFields(request("-5", "ZAR", "ZA"))).containsExactly("amount");
		assertThat(invalidFields(request("1.23456", "ZAR", "ZA"))).containsExactly("amount");
		assertThat(invalidFields(request("1.2345", "ZAR", "ZA"))).isEmpty();
	}

	@Test
	void currencyMustBeAThreeLetterUpperCaseCode() {
		assertThat(invalidFields(request("5", "ZAR", "ZA"))).isEmpty();
		assertThat(invalidFields(request("5", "USD", "US"))).isEmpty();
		assertThat(invalidFields(request("5", "zar", "ZA"))).containsExactly("currency");
		assertThat(invalidFields(request("5", "ZA", "ZA"))).containsExactly("currency");
		assertThat(invalidFields(request("5", "ZARR", "ZA"))).containsExactly("currency");
	}

	@Test
	void transactionIdMayOnlyContainUrlSafeCharacters() {
		assertThat(invalidFields(withId("txn-2026.03_01"))).isEmpty();
		assertThat(invalidFields(withId("txn 1"))).containsExactly("transactionId");
		assertThat(invalidFields(withId("a/b"))).containsExactly("transactionId");
		assertThat(invalidFields(withId("50%"))).containsExactly("transactionId");
		assertThat(invalidFields(withId("a#b"))).containsExactly("transactionId");
	}

	private static TransactionRequest withId(String id) {
		return new TransactionRequest(id, "acc-1", BigDecimal.ONE, "ZAR", TransactionCategory.RETAIL, null, "ZA",
				OffsetDateTime.parse("2026-03-01T12:00:00Z"));
	}

	@Test
	void countryMustBeAnIsoAlpha2Code() {
		assertThat(invalidFields(request("5", "ZAR", "ZAF"))).containsExactly("country");
	}

	@Test
	void requiredFieldsAreEnforced() {
		var empty = new TransactionRequest(" ", "", null, null, null, null, null, null);

		assertThat(invalidFields(empty)).containsExactlyInAnyOrder("transactionId", "accountId", "amount", "currency",
				"category", "country", "timestamp");
	}

	@Test
	void idsAreLimitedTo64Characters() {
		var longId = "x".repeat(65);
		var request = new TransactionRequest(longId, longId, BigDecimal.ONE, "ZAR", TransactionCategory.RETAIL, null,
				"ZA", OffsetDateTime.now(ZoneOffset.UTC));

		assertThat(invalidFields(request)).containsExactlyInAnyOrder("transactionId", "accountId");
	}
}
