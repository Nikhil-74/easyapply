package com.easyapply.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.easyapply.config.DataProperties;
import com.easyapply.config.MailProperties;
import com.easyapply.config.UserProperties;
import com.easyapply.dto.BulkEmailDraft;
import com.easyapply.dto.BulkEmailDraftUpdate;
import com.easyapply.dto.RecruiterRecipient;
import com.easyapply.dto.ResumeAttachmentInfo;
import com.easyapply.util.ResourcePathResolver;

@Service
public class BulkEmailDraftService {

	private static final String SUBJECT_KEY = "easyapply.mail.subject";

	private final DataProperties dataProperties;
	private final MailProperties mailProperties;
	private final UserProperties userProperties;
	private final ResourcePathResolver resourcePathResolver;

	public BulkEmailDraftService(
			DataProperties dataProperties,
			MailProperties mailProperties,
			UserProperties userProperties,
			ResourcePathResolver resourcePathResolver) {
		this.dataProperties = dataProperties;
		this.mailProperties = mailProperties;
		this.userProperties = userProperties;
		this.resourcePathResolver = resourcePathResolver;
	}

	public BulkEmailDraft getDraft() throws IOException {
		return new BulkEmailDraft(
				readRecipients(),
				Objects.toString(mailProperties.getSubject(), ""),
				readText(dataProperties.getMailTemplate()),
				getResumeInfo());
	}

	public BulkEmailDraft saveDraft(BulkEmailDraftUpdate update) throws IOException {
		if (update.recipients() != null) {
			saveRecipients(update.recipients());
		}

		if (update.subject() != null) {
			saveSubject(update.subject());
		}

		if (update.bodyTemplate() != null) {
			writeText(dataProperties.getMailTemplate(), update.bodyTemplate());
		}

		return getDraft();
	}

	public Path resolveResumePath() {
		String resumePath = userProperties.getResumePath();
		if (resumePath == null || resumePath.isBlank()) {
			return null;
		}
		return resourcePathResolver.resolveExternalPath(resumePath);
	}

	public ResumeAttachmentInfo getResumeInfo() {
		Path resumePath = resolveResumePath();
		if (resumePath == null) {
			return new ResumeAttachmentInfo("", "", 0, false, "Resume path is not configured.");
		}

		if (!Files.exists(resumePath)) {
			return new ResumeAttachmentInfo(
					resumePath.getFileName().toString(),
					resumePath.toString(),
					0,
					false,
					"Resume file was not found.");
		}

		try {
			return new ResumeAttachmentInfo(
					resumePath.getFileName().toString(),
					resumePath.toString(),
					Files.size(resumePath),
					true,
					"Attached to every bulk email.");
		} catch (IOException ex) {
			return new ResumeAttachmentInfo(
					resumePath.getFileName().toString(),
					resumePath.toString(),
					0,
					false,
					"Resume file could not be read: " + ex.getMessage());
		}
	}

	private List<RecruiterRecipient> readRecipients() throws IOException {
		String content = readText(dataProperties.getRecruiterList());
		List<RecruiterRecipient> recipients = new ArrayList<>();

		for (String line : content.split("\\R")) {
			if (line.isBlank()) {
				continue;
			}

			String[] parts = line.split(",", 2);
			recipients.add(new RecruiterRecipient(parts[0].trim(), parts.length > 1 ? parts[1].trim() : ""));
		}

		return recipients;
	}

	private void saveRecipients(List<RecruiterRecipient> recipients) throws IOException {
		String content = recipients.stream()
				.filter(recipient -> recipient.email() != null && !recipient.email().isBlank())
				.map(recipient -> recipient.email().trim() + "," + Objects.toString(recipient.name(), "").trim())
				.collect(Collectors.joining(System.lineSeparator()));
		writeText(dataProperties.getRecruiterList(), content + System.lineSeparator());
	}

	private void saveSubject(String subject) throws IOException {
		mailProperties.setSubject(subject);
		Path localProperties = Paths.get(System.getProperty("user.dir")).resolve("application-local.properties");
		List<String> lines = Files.exists(localProperties)
				? Files.readAllLines(localProperties, StandardCharsets.UTF_8)
				: new ArrayList<>();
		String subjectLine = SUBJECT_KEY + "=" + subject;
		boolean replaced = false;

		for (int index = 0; index < lines.size(); index++) {
			String line = lines.get(index);
			if (line.trim().startsWith(SUBJECT_KEY + "=")) {
				lines.set(index, subjectLine);
				replaced = true;
				break;
			}
		}

		if (!replaced) {
			if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
				lines.add("");
			}
			lines.add(subjectLine);
		}
		Files.write(localProperties, lines, StandardCharsets.UTF_8);
	}

	private String readText(String location) throws IOException {
		Path sourcePath = sourceResourcePath(location);
		if (sourcePath != null && Files.exists(sourcePath)) {
			return Files.readString(sourcePath, StandardCharsets.UTF_8);
		}
		return resourcePathResolver.readResourceAsString(location);
	}

	private void writeText(String location, String content) throws IOException {
		Path sourcePath = sourceResourcePath(location);
		if (sourcePath != null) {
			Files.createDirectories(sourcePath.getParent());
			Files.writeString(sourcePath, content, StandardCharsets.UTF_8);
		}

		Resource resource = resourcePathResolver.loadResource(location);
		if (resource.isFile()) {
			Path runtimePath = resource.getFile().toPath();
			if (!runtimePath.equals(sourcePath)) {
				Files.writeString(runtimePath, content, StandardCharsets.UTF_8);
			}
		}
	}

	private Path sourceResourcePath(String location) {
		if (location == null || !location.startsWith("classpath:")) {
			return null;
		}

		String relativePath = location.substring("classpath:".length());
		while (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
			relativePath = relativePath.substring(1);
		}
		return Paths.get(System.getProperty("user.dir")).resolve("src/main/resources").resolve(relativePath);
	}
}
