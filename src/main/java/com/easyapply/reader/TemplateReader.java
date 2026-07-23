package com.easyapply.reader;

import java.io.IOException;

import org.springframework.stereotype.Component;

import com.easyapply.config.DataProperties;
import com.easyapply.util.ResourcePathResolver;

import jakarta.annotation.PostConstruct;

@Component
public class TemplateReader {

	private final DataProperties dataProperties;
	private final ResourcePathResolver resourcePathResolver;

	private String mailTemplate;
	private String scoringPromptTemplate;
	private String aiExtractionSystemPromptTemplate;

	public TemplateReader(DataProperties dataProperties, ResourcePathResolver resourcePathResolver) {
		this.dataProperties = dataProperties;
		this.resourcePathResolver = resourcePathResolver;
	}

	@PostConstruct
	public void initTemplate() {
		try {
			this.mailTemplate = resourcePathResolver.readResourceAsString(dataProperties.getMailTemplate());
			this.scoringPromptTemplate = resourcePathResolver.readResourceAsString(dataProperties.getScoringPrompt());
			this.aiExtractionSystemPromptTemplate = resourcePathResolver
					.readResourceAsString(dataProperties.getAiExtractionSystemPrompt());
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load one or more application templates on startup", e);
		}
	}

	public String getScoringPromptTemplate() {
		return this.scoringPromptTemplate;
	}

	public String getAiExtractionSystemPromptTemplate() {
		return this.aiExtractionSystemPromptTemplate;
	}

	public String render(String recruiterName) {
		return mailTemplate.replace("{recruiterName}", recruiterName);
	}
}