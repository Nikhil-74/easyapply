package com.easyapply.dto;

public record UserProfileSettings(
		String email,
		String maskedPassword,
		boolean passwordConfigured,
		ResumeAttachmentInfo resume) {
}
