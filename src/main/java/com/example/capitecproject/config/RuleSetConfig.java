package com.example.capitecproject.config;

import com.example.capitecproject.repository.FraudAssessmentRepository;
import com.example.capitecproject.rules.ActiveRule;
import com.example.capitecproject.rules.AmountThresholdRule;
import com.example.capitecproject.rules.CategoryAmountRule;
import com.example.capitecproject.rules.CountryListRule;
import com.example.capitecproject.rules.FraudRule;
import com.example.capitecproject.rules.OddHoursRule;
import com.example.capitecproject.rules.RuleSet;
import com.example.capitecproject.rules.VelocityRule;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the rule set from {@code fraud.rules}, which is loaded from {@code fraud-rules.yml}. Fails startup if invalid. */
@Configuration
public class RuleSetConfig {

	static final String RULE_FILE = "fraud-rules.yml";

	@Bean
	RuleSet ruleSet(FraudProperties properties, FraudAssessmentRepository repository, Clock clock) throws IOException {
		List<ActiveRule> rules = properties.rules().stream()
				.map(def -> new ActiveRule(def.code(), create(def, repository, clock), def.shadow()))
				.toList();
		return new RuleSet(rules, version());
	}

	static FraudRule create(RuleDefinition def, FraudAssessmentRepository repository, Clock clock) {
		return switch (def.type()) {
			case AMOUNT_THRESHOLD -> new AmountThresholdRule(def.code(), def.threshold(), def.score());
			case CATEGORY_AMOUNT -> new CategoryAmountRule(def.code(), def.categories(), def.minAmount(), def.score());
			case COUNTRY_LIST -> new CountryListRule(def.code(), def.countries(), def.score());
			case VELOCITY -> new VelocityRule(def.code(), def.maxTransactions(), def.window(), def.score(), repository,
					clock);
			case ODD_HOURS -> new OddHoursRule(def.code(), def.startHour(), def.endHour(), def.minAmount(), def.score(),
					def.zone());
		};
	}

	/** The first 12 hex characters of the SHA-256 of the rule file, so any edit to the file changes the version. */
	static String version() throws IOException {
		try (InputStream in = new ClassPathResource(RULE_FILE).getInputStream()) {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
			return HexFormat.of().formatHex(digest).substring(0, 12);
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
