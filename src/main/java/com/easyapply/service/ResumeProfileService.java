package com.easyapply.service;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.easyapply.config.AiProperties;
import com.easyapply.exception.ResumeReadException;
import com.easyapply.model.ResumeProfile;
import com.easyapply.reader.ResumeReader;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ResumeProfileService {

	private static final String SYSTEM_PROMPT = """
			You extract structured candidate data from a resume.
			Return exactly one JSON object and nothing else.
			No markdown, no code fences, no explanation.

			JSON schema:
			{
			  "name": "string",
			  "yearsOfExperience": "string",
			  "currentRole": "string",
			  "skills": ["string"],
			  "summary": "string"
			}

			Rules:
			- name: candidate full name
			- yearsOfExperience: total experience as written (example: "4 years")
			- currentRole: most recent job title
			- skills: unique technical and professional skills from resume only
			- summary: 2-3 sentence professional summary based on resume facts only
			- Never invent skills or experience not present in the resume
			""";

	private final ResumeReader resumeReader;
	private final ChatClient chatClient;
	private final AiProperties aiProperties;
	private final ObjectMapper objectMapper;

	private volatile ResumeProfile cachedProfile;

	public ResumeProfileService(
			ResumeReader resumeReader,
			ChatClient.Builder builder,
			AiProperties aiProperties,
			ObjectMapper objectMapper) {
		this.resumeReader = resumeReader;
		this.chatClient = builder.build();
		this.aiProperties = aiProperties;
		this.objectMapper = objectMapper;
	}

	@Async
	public ResumeProfile getProfile() {
		if (cachedProfile != null) {
			return cachedProfile;
		}
		synchronized (this) {
			if (cachedProfile == null) {
				cachedProfile = extractProfile(resumeReader.readResumeText());
			}
		}
		return cachedProfile;
	}

	public void clearCache() {
		cachedProfile = null;
	}

	private ResumeProfile extractProfile(String resumeText) {
		String truncated = truncate(resumeText);
		String userPrompt = """
				Extract candidate profile from this resume.

				RESUME:
				---
				%s
				---

				Return one JSON object only.
				""".formatted(truncated);

		try {
			String rawResponse = chatClient.prompt()
					.system(SYSTEM_PROMPT)
					.user(userPrompt)
					.call()
					.content();

			ResumeProfile profile = objectMapper.readValue(extractJson(rawResponse), ResumeProfile.class);
			normalize(profile);
			return profile;
		} catch (Exception ex) {
			throw new ResumeReadException("Failed to extract resume profile", ex);
		}
	}

	private void normalize(ResumeProfile profile) {
		if (profile.getSkills() != null) {
			profile.setSkills(profile.getSkills().stream()
					.filter(skill -> skill != null && !skill.isBlank())
					.map(String::trim)
					.distinct()
					.toList());
		} else {
			profile.setSkills(List.of());
		}
	}

	private String extractJson(String content) {
		String trimmed = content.trim();
		if (trimmed.startsWith("```")) {
			trimmed = trimmed.replaceAll("(?s)^```(?:json)?\\s*", "");
			trimmed = trimmed.replaceAll("```\\s*$", "").trim();
		}
		int start = trimmed.indexOf('{');
		int end = trimmed.lastIndexOf('}');
		if (start >= 0 && end > start) {
			return trimmed.substring(start, end + 1);
		}
		return trimmed;
	}

	private String truncate(String text) {
		int maxLength = aiProperties.getMaxPostTextLength();
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength) + "\n...[truncated]";
	}
}
