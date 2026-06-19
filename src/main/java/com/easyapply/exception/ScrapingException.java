package com.easyapply.exception;

public class ScrapingException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ScrapingException(String message) {
		super(message);
	}

	public ScrapingException(String message, Throwable cause) {
		super(message, cause);
	}
}
