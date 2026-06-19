package com.easyapply.dto;

import java.util.List;

public record BulkEmailDraft(
		List<RecruiterRecipient> recipients,
		String subject,
		String bodyTemplate,
		ResumeAttachmentInfo resume) {
}
