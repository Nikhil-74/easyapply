package com.easyapply.dto;

import java.util.List;

public record SentEmailHistoryItem(
		String sentAt,
		String jobTitle,
		String recruiterName,
		String recipientEmail,
		String emailSubject,
		List<String> contactEmails) {
}
