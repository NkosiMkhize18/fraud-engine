package com.example.capitecproject.service;

public class AssessmentNotFoundException extends RuntimeException {

	public AssessmentNotFoundException(String transactionId) {
		super("No assessment found for transaction " + transactionId);
	}
}
