package com.example.capitecproject.rules;

import java.util.List;

/**
 * The rules the service is running, loaded once at startup. {@code version} identifies the exact rule file (a hash of
 * its contents), and every assessment records it, so a decision can be traced to the rules that made it.
 */
public record RuleSet(List<ActiveRule> rules, String version) {

	public RuleSet {
		rules = List.copyOf(rules);
	}
}
