package com.example.capitecproject.config;

/**
 * The kinds of rule the service knows how to run. A rule in the rule file must be one of these; adding a new kind
 * of logic is a code change, reviewed like any other.
 */
public enum RuleType {
	/** Converted amount at or above {@code threshold}. */
	AMOUNT_THRESHOLD,
	/** One of {@code categories} and converted amount at or above {@code min-amount}. */
	CATEGORY_AMOUNT,
	/** Country is one of {@code countries}. */
	COUNTRY_LIST,
	/** More than {@code max-transactions} on the account within {@code window}, counting arrival time. */
	VELOCITY,
	/** Converted amount at or above {@code min-amount} between {@code start-hour} and {@code end-hour} in {@code zone}. */
	ODD_HOURS
}
