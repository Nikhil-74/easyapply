package com.easyapply.serviceImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.easyapply.model.JobPost;
import com.easyapply.model.ResumeProfile;
import com.easyapply.service.AiScoreAdjuster;

@Service
public class DefaultAiScoreAdjuster implements AiScoreAdjuster {

	private static final Logger log = LoggerFactory.getLogger(DefaultAiScoreAdjuster.class);

	private final ChatClient chatClient;

	public DefaultAiScoreAdjuster(ChatClient.Builder builder) {
		this.chatClient = builder.build();
	}

	@Override
	public int adjustScore(JobPost job, ResumeProfile profile, int keywordScore) {

		String prompt = """
				You are a technical recruiter.

				Analyze the job requirements and candidate profile.

				Job Title: %s
				Experience Level: %s
				Required Skills: %s

				Candidate Skills: %s
				Candidate Experience: %s

				Return ONLY a number from 0 to 100 indicating the suitability score.
				""".formatted(job.getJobTitle(), job.getExperienceRequired(), job.getRequiredSkills(),
				profile.getSkills(), profile.getYearsOfExperience());

		try {

			String response = chatClient.prompt().user(prompt).call().content();

			return parseScore(response);

		} catch (Exception ex) {

			log.warn("AI scoring failed. Falling back to keyword score.");

			return keywordScore;
		}
	}

	private int parseScore(String response) {

		if (response == null) {
			return 0;
		}

		String number = response.replaceAll("[^0-9]", "");

		if (number.isBlank()) {
			return 0;
		}

		try {

			return Math.max(0, Math.min(Integer.parseInt(number), 100));

		} catch (NumberFormatException ex) {

			return 0;
		}
	}
}