package com.easyapply.reader;

import java.io.IOException;

import org.springframework.stereotype.Component;

import com.easyapply.config.DataProperties;
import com.easyapply.util.ResourcePathResolver;

@Component
public class MailTemplateReader {

	private final DataProperties dataProperties;
	private final ResourcePathResolver resourcePathResolver;

	public MailTemplateReader(DataProperties dataProperties, ResourcePathResolver resourcePathResolver) {
		this.dataProperties = dataProperties;
		this.resourcePathResolver = resourcePathResolver;
	}

	public String render(String recruiterName) throws IOException {
		String template = resourcePathResolver.readResourceAsString(dataProperties.getMailTemplate());
		return template.replace("{recruiterName}", recruiterName);
	}
}
