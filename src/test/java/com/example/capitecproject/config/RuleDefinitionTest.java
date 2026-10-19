package com.example.capitecproject.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.capitecproject.domain.TransactionCategory;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class RuleDefinitionTest {

	private static RuleDefinition amountThreshold(BigDecimal threshold) {
		return new RuleDefinition("HIGH_AMOUNT", RuleType.AMOUNT_THRESHOLD, 40, false, threshold, null, null, null, null,
				null, null, null, null);
	}

	@Test
	void theRuleFileShippedWithTheServiceBindsToValidRules() throws Exception {
		var sources = new YamlPropertySourceLoader().load("fraud-rules", new ClassPathResource("fraud-rules.yml"));
		var binder = new Binder(ConfigurationPropertySources.from(sources));

		List<RuleDefinition> rules = binder.bind("fraud.rules", Bindable.listOf(RuleDefinition.class)).get();

		assertThat(rules).extracting(RuleDefinition::code)
				.containsExactly("HIGH_AMOUNT", "RISKY_CATEGORY", "VELOCITY", "HIGH_RISK_COUNTRY", "ODD_HOURS");
		assertThat(rules.get(0).threshold()).isEqualByComparingTo("10000");
		assertThat(rules.get(2).window()).isEqualTo(Duration.ofMinutes(10));
		assertThat(rules.get(4).zone()).isEqualTo(ZoneId.of("Africa/Johannesburg"));
		assertThat(rules).allMatch(rule -> !rule.shadow());
	}

	@Test
	void aRuleMissingARequiredParameterIsRefused() {
		assertThatThrownBy(() -> amountThreshold(null)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("AMOUNT_THRESHOLD requires [threshold]");
	}

	@Test
	void aParameterThatBelongsToAnotherTypeIsRefusedNotIgnored() {
		assertThatThrownBy(() -> new RuleDefinition("HIGH_AMOUNT", RuleType.AMOUNT_THRESHOLD, 40, false,
				new BigDecimal("10000"), Set.of(TransactionCategory.GAMBLING), null, null, null, null, null, null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("does not take [categories]");
	}

	@Test
	void nonsenseValuesAreRefused() {
		assertThatThrownBy(() -> amountThreshold(BigDecimal.ZERO)).hasMessageContaining("threshold must be positive");
		assertThatThrownBy(() -> new RuleDefinition("VELOCITY", RuleType.VELOCITY, 35, false, null, null, null, null,
				5, Duration.ZERO, null, null, null)).hasMessageContaining("window must be positive");
		assertThatThrownBy(() -> new RuleDefinition("ODD_HOURS", RuleType.ODD_HOURS, 20, false, null, null,
				new BigDecimal("2000"), null, null, null, 25, 5, ZoneId.of("UTC")))
				.hasMessageContaining("start-hour must be 0-23");
		// A zero score is rejected by Bean Validation when the settings load (see FraudProperties' @Valid rules).
		var zeroScore = new RuleDefinition("HIGH_AMOUNT", RuleType.AMOUNT_THRESHOLD, 0, false,
				new BigDecimal("10000"), null, null, null, null, null, null, null, null);
		assertThat(jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator().validate(zeroScore))
				.extracting(violation -> violation.getPropertyPath().toString())
				.containsExactly("score");
	}

	@Test
	void countriesAreNormalisedToUpperCase() {
		var rule = new RuleDefinition("HIGH_RISK_COUNTRY", RuleType.COUNTRY_LIST, 30, false, null, null, null,
				Set.of("kp", "Ir"), null, null, null, null, null);

		assertThat(rule.countries()).containsExactlyInAnyOrder("KP", "IR");
	}

	@Test
	void duplicateRuleCodesAreRefusedWhenTheSettingsAreBuilt() {
		var first = amountThreshold(new BigDecimal("10000"));
		var second = amountThreshold(new BigDecimal("5000"));
		var timestamps = new FraudProperties.Timestamps(Duration.ofHours(24), Duration.ofMinutes(5));

		assertThatThrownBy(() -> new FraudProperties(50, Duration.ofSeconds(1), "ZAR", List.of(first, second), timestamps))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Duplicate rule code HIGH_AMOUNT");
	}
}
