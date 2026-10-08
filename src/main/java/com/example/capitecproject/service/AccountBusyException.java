package com.example.capitecproject.service;

public class AccountBusyException extends RuntimeException {

	public AccountBusyException(Throwable cause) {
		super("Too many concurrent requests for this account, retry shortly", cause);
	}
}
