package com.example.capitecproject.exception;

public class DuplicateTransactionException extends RuntimeException {

	public DuplicateTransactionException(String transactionId) {
		super("Transaction " + transactionId + " was already submitted with different details");
	}
}
