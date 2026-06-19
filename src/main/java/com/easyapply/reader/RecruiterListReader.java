package com.easyapply.reader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.easyapply.config.DataProperties;
import com.easyapply.util.ResourcePathResolver;

@Component
public class RecruiterListReader {

	private final DataProperties dataProperties;
	private final ResourcePathResolver resourcePathResolver;

	public RecruiterListReader(DataProperties dataProperties, ResourcePathResolver resourcePathResolver) {
		this.dataProperties = dataProperties;
		this.resourcePathResolver = resourcePathResolver;
	}

	public List<String[]> readRecipients() throws IOException {
		Resource resource = resourcePathResolver.loadResource(dataProperties.getRecruiterList());
		List<String[]> recipients = new ArrayList<>();

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				recipients.add(line.split(",", 2));
			}
		}

		return recipients;
	}
}
