package com.easyapply.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.easyapply.config.UserProperties;
import com.easyapply.dto.ResumeAttachmentInfo;
import com.easyapply.dto.UserProfileSettings;
import com.easyapply.dto.UserProfileSettingsUpdate;
import com.easyapply.util.ResourcePathResolver;

@Service
public class UserProfileSettingsService {

	private static final String EMAIL_KEY = "easyapply.user.email";
	private static final String PASSWORD_KEY = "easyapply.user.password";
	private static final String RESUME_PATH_KEY = "easyapply.user.resume-path";

	private final UserProperties userProperties;
	private final ResourcePathResolver resourcePathResolver;
	private final Path localProperties;
	private final Path uploadDirectory;

	public UserProfileSettingsService(UserProperties userProperties, ResourcePathResolver resourcePathResolver) {
		this.userProperties = userProperties;
		this.resourcePathResolver = resourcePathResolver;
		Path projectRoot = Paths.get(System.getProperty("user.dir"));
		this.localProperties = projectRoot.resolve("application-local.properties");
		this.uploadDirectory = projectRoot.resolve("resumes");
	}

	@Async
	public UserProfileSettings getSettings() {
		String password = userProperties.getPassword();
		return new UserProfileSettings(
				Objects.toString(userProperties.getEmail(), ""),
				mask(password),
				password != null && !password.isBlank(),
				getResumeInfo());
	}

	public UserProfileSettings saveSettings(UserProfileSettingsUpdate update) throws IOException {
		if (update.email() != null) {
			userProperties.setEmail(update.email().trim());
			saveProperty(EMAIL_KEY, update.email().trim());
		}

		if (update.password() != null && !update.password().isBlank()) {
			userProperties.setPassword(update.password());
			saveProperty(PASSWORD_KEY, update.password());
		}

		return getSettings();
	}

	public UserProfileSettings uploadResume(MultipartFile resume) throws IOException {
		if (resume == null || resume.isEmpty()) {
			throw new IllegalArgumentException("Choose a resume PDF before uploading.");
		}

		String originalName = Objects.toString(resume.getOriginalFilename(), "resume.pdf");
		String safeName = originalName.replaceAll("[^A-Za-z0-9._-]", "_");
		if (!safeName.toLowerCase().endsWith(".pdf")) {
			throw new IllegalArgumentException("Only PDF resumes are supported.");
		}

		Files.createDirectories(uploadDirectory);
		Path destination = uploadDirectory.resolve(safeName).normalize();
		if (!destination.startsWith(uploadDirectory)) {
			throw new IllegalArgumentException("Invalid resume file name.");
		}

		Files.copy(resume.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
		String relativePath = Paths.get(System.getProperty("user.dir")).relativize(destination).toString();
		userProperties.setResumePath(relativePath);
		saveProperty(RESUME_PATH_KEY, relativePath);
		return getSettings();
	}

	private ResumeAttachmentInfo getResumeInfo() {
		String resumePath = userProperties.getResumePath();
		if (resumePath == null || resumePath.isBlank()) {
			return new ResumeAttachmentInfo("", "", 0, false, "Resume path is not configured.");
		}

		Path resolvedPath = resourcePathResolver.resolveExternalPath(resumePath);
		if (!Files.exists(resolvedPath)) {
			return new ResumeAttachmentInfo(
					resolvedPath.getFileName().toString(),
					resolvedPath.toString(),
					0,
					false,
					"Resume file was not found.");
		}

		try {
			return new ResumeAttachmentInfo(
					resolvedPath.getFileName().toString(),
					resolvedPath.toString(),
					Files.size(resolvedPath),
					true,
					"Used for matched and bulk emails.");
		} catch (IOException ex) {
			return new ResumeAttachmentInfo(
					resolvedPath.getFileName().toString(),
					resolvedPath.toString(),
					0,
					false,
					"Resume file could not be read: " + ex.getMessage());
		}
	}

	private void saveProperty(String key, String value) throws IOException {
		List<String> lines = Files.exists(localProperties)
				? Files.readAllLines(localProperties, StandardCharsets.UTF_8)
				: new ArrayList<>();
		String propertyLine = key + "=" + value;
		boolean replaced = false;

		for (int index = 0; index < lines.size(); index++) {
			if (lines.get(index).trim().startsWith(key + "=")) {
				lines.set(index, propertyLine);
				replaced = true;
				break;
			}
		}

		if (!replaced) {
			if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
				lines.add("");
			}
			lines.add(propertyLine);
		}

		Files.write(localProperties, lines, StandardCharsets.UTF_8);
	}

	private String mask(String value) {
		if (value == null || value.isBlank()) {
			return "Not configured";
		}
		return "**** **** **** " + value.substring(Math.max(0, value.length() - 4));
	}
}
