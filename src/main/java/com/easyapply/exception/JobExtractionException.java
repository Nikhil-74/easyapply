package com.easyapply.exception;

public class JobExtractionException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public JobExtractionException(String message) {
		super(message);
	}

	public JobExtractionException(String message, Throwable cause) {
		super(message, cause);
	}
}
