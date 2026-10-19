package com.example.capitecproject.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.capitecproject.config.FraudProperties;
import com.example.capitecproject.domain.ExchangeRate;
import com.example.capitecproject.exception.InvalidTransactionException;
import com.example.capitecproject.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CurrencyConverterTest {

	private final ExchangeRateRepository rates = mock(ExchangeRateRepository.class);
	private final FraudProperties properties = new FraudProperties(50, Duration.ofSeconds(1), "ZAR", null,
			new FraudProperties.Timestamps(Duration.ofHours(24), Duration.ofMinutes(5)));
	private final CurrencyConverter converter = new CurrencyConverter(rates, properties);

	private void rate(String currency, String rateToBase) {
		when(rates.findById(currency)).thenReturn(Optional.of(new ExchangeRate(currency, new BigDecimal(rateToBase))));
	}

	@Test
	void baseCurrencyIsNotLookedUpAndNeedsNoRate() {
		var conversion = converter.convert("ZAR", new BigDecimal("150.5"));

		assertThat(conversion.baseAmount()).isEqualByComparingTo("150.5").hasScaleOf(4);
		assertThat(conversion.rateToBase()).isEqualByComparingTo("1");
		verifyNoInteractions(rates);
	}

	@Test
	void foreignAmountIsMultipliedByTheRateAndRoundedHalfUpToFourPlaces() {
		rate("EUR", "19.87654321");

		var conversion = converter.convert("EUR", new BigDecimal("0.01"));

		assertThat(conversion.baseAmount()).isEqualByComparingTo("0.1988");
		assertThat(conversion.rateToBase()).isEqualByComparingTo("19.87654321");
	}

	@Test
	void currencyWithoutARateIsUnsupported() {
		when(rates.findById("XYZ")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> converter.convert("XYZ", BigDecimal.TEN))
				.isInstanceOf(InvalidTransactionException.class)
				.hasMessage("Unsupported currency XYZ");
	}

	@Test
	void amountsWithMoreDecimalPlacesThanTheCurrencyHasAreRejected() {
		rate("JPY", "0.1");
		assertThatThrownBy(() -> converter.convert("JPY", new BigDecimal("100.5")))
				.isInstanceOf(InvalidTransactionException.class)
				.hasMessageContaining("more decimal places than JPY allows (0)");

		assertThatThrownBy(() -> converter.convert("USD", new BigDecimal("1.001")))
				.isInstanceOf(InvalidTransactionException.class)
				.hasMessageContaining("USD allows (2)");
	}

	@Test
	void trailingZerosDoNotCountAsExtraDecimalPlaces() {
		rate("JPY", "0.1");

		assertThat(converter.convert("JPY", new BigDecimal("100.00")).baseAmount()).isEqualByComparingTo("10");
	}

	@Test
	void threeDecimalCurrenciesKeepTheirMinorUnits() {
		rate("KWD", "60.5");

		assertThat(converter.convert("KWD", new BigDecimal("1.125")).baseAmount()).isEqualByComparingTo("68.0625");
	}
}
