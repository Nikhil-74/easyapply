package com.easyapply.exception;

public class ResumeReadException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ResumeReadException(String message) {
		super(message);
	}

	public ResumeReadException(String message, Throwable cause) {
		super(message, cause);
	}
}
