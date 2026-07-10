package com.easyapply.reader;

import java.io.IOException;

import org.springframework.stereotype.Component;

import com.easyapply.config.DataProperties;
import com.easyapply.util.ResourcePathResolver;

import jakarta.annotation.PostConstruct;

@Component
public class MailTemplateReader {

	private final DataProperties dataProperties;
	private final ResourcePathResolver resourcePathResolver;

	private String cachedTemplate;

	public MailTemplateReader(DataProperties dataProperties, ResourcePathResolver resourcePathResolver) {
		this.dataProperties = dataProperties;
		this.resourcePathResolver = resourcePathResolver;
	}

	@PostConstruct
	public void initTemplate() {
		try {
			this.cachedTemplate = resourcePathResolver.readResourceAsString(dataProperties.getMailTemplate());
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load email template on startup", e);
		}
	}

	public String render(String recruiterName) {
		return cachedTemplate.replace("{recruiterName}", recruiterName);
	}
}
