package com.easyapply.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.easyapply.config.AiProperties;
import com.easyapply.exception.JobExtractionException;
import com.easyapply.model.JobPost;

@Service
public class AiExtractionService {

	private static final String SYSTEM_PROMPT = """
			You are a precise information extraction engine.

			Extract structured data from the LinkedIn hiring post provided by the user.

			Return EXACTLY one valid JSON object and nothing else.

			STRICT REQUIREMENTS:
			- Return only JSON.
			- Do not wrap the JSON in markdown or code fences.
			- Do not include explanations, comments, notes, or additional text.
			- Do not invent or infer information.
			- Use only information explicitly present in the input.
			- If a value cannot be determined, use an empty string ("").
			- For array fields with no values, return an empty array ([]).
			- Remove duplicate values from arrays.

			JSON format:

			{
			  "jobTitle": "",
			  "recruiterName": "",
			  "recruiterProfile": "",
			  "experienceRequired": "",
			  "requiredSkills": [],
			  "location": [],
			  "contactEmails": []
			}

			Field Extraction Rules:

			- jobTitle:
			  Primary role being hired for.

			- recruiterName:
			  Name of the recruiter, hiring person, or post author if explicitly mentioned.

			- recruiterProfile:
			  LinkedIn profile URL if present.

			- experienceRequired:
			  Copy the exact experience phrase as written in the post.

			- requiredSkills:
			  Extract unique technical and professional skills explicitly mentioned.

			- location:
			  Extract all job locations mentioned.

			- contactEmails:
			  Extract every valid email address present in the post.
			  Also extract emails listed under an 'Emails Found:' section.

			Additional Rules:

			- Preserve original capitalization where possible.
			- Do not generate fields not present in the schema.
			- If the content is not a hiring or job-related post, return the schema with empty values.
			- Return exactly one JSON object.
			- When multiple job roles are mentioned, select the primary role being actively hired.
			- When multiple experience requirements are mentioned, extract the one associated with the selected job role.
			- Do not include generic words such as "communication", "team player", or "good attitude" in requiredSkills unless explicitly listed as required skills.
			""";

	private static final Pattern RANGE_PATTERN = Pattern
			.compile("(\\d+(?:\\.\\d+)?)\\s*(?:-|to)\\s*(\\d+(?:\\.\\d+)?)");

	private static final Pattern PLUS_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*\\+");

	private static final Pattern SINGLE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

	private final ChatClient chatClient;
	private final AiProperties aiProperties;

	public AiExtractionService(ChatClient.Builder builder, AiProperties aiProperties) {
		this.chatClient = builder.build();
		this.aiProperties = aiProperties;
	}

	public JobPost extract(String postText) {

		String userPrompt = """
				Analyze the following LinkedIn hiring post and extract the requested fields.

				POST:
				%s

				Return only a valid JSON object matching the schema defined in the system prompt.
				""".formatted(postText);

		try {
			JobPost job = chatClient.prompt().system(SYSTEM_PROMPT).user(userPrompt).call().entity(JobPost.class);
			return job;
		} catch (JobExtractionException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new JobExtractionException("Failed to extract job post data", ex);
		}
	}

	public boolean matchesTargetExperience(JobPost job) {
		if (job == null || job.getExperienceRequired() == null || job.getExperienceRequired().isBlank()) {
			return false;
		}

		String experience = job.getExperienceRequired().toLowerCase(Locale.ROOT);

		double[] range = extractExperienceRange(experience);
		if (range == null) {
			return false;
		}

		double targetMin = aiProperties.getMinExperienceYears();
		double targetMax = aiProperties.getMaxExperienceYears();

		double expMin = range[0];
		double expMax = range[1];

		return expMax >= targetMin && expMin <= targetMax;
	}

	private double[] extractExperienceRange(String experience) {

		Matcher rangeMatcher = RANGE_PATTERN.matcher(experience);
		if (rangeMatcher.find()) {
			return new double[] { Double.parseDouble(rangeMatcher.group(1)),
					Double.parseDouble(rangeMatcher.group(2)) };
		}

		Matcher plusMatcher = PLUS_PATTERN.matcher(experience);
		if (plusMatcher.find()) {
			return new double[] { Double.parseDouble(plusMatcher.group(1)), Double.MAX_VALUE };
		}

		Matcher singleMatcher = SINGLE_PATTERN.matcher(experience);
		if (singleMatcher.find()) {
			double years = Double.parseDouble(singleMatcher.group(1));
			return new double[] { years, years };
		}

		return null;
	}

}
