package com.easyapply.dto;

import java.util.List;

public record BulkEmailDraftUpdate(
		List<RecruiterRecipient> recipients,
		String subject,
		String bodyTemplate) {
}
