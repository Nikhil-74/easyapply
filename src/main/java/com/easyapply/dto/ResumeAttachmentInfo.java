package com.easyapply.dto;

public record ResumeAttachmentInfo(
		String fileName,
		String path,
		long sizeBytes,
		boolean available,
		String message) {
}
