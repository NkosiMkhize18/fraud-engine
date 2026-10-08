package com.example.capitecproject.api;

import com.example.capitecproject.domain.TransactionCategory;
import com.example.capitecproject.domain.TransactionEvent;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionRequest(
		@NotBlank @Size(max = 64) String transactionId,
		@NotBlank @Size(max = 64) String accountId,
		@NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 15, fraction = 4) BigDecimal amount,
		@NotNull @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO 4217 code") String currency,
		@NotNull TransactionCategory category,
		@Size(max = 128) String merchant,
		@NotNull @Pattern(regexp = "[A-Z]{2}", message = "must be a 2-letter ISO 3166 code") String country,
		@NotNull Instant timestamp) {

	TransactionEvent toEvent() {
		return new TransactionEvent(transactionId, accountId, amount, currency, category, merchant, country,
				timestamp);
	}
}
