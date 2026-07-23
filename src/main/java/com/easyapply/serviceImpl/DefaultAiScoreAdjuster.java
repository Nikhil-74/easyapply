package com.easyapply.serviceImpl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.easyapply.model.JobPost;
import com.easyapply.model.ResumeProfile;
import com.easyapply.reader.TemplateReader;
import com.easyapply.service.AiScoreAdjuster;

@Service
public class DefaultAiScoreAdjuster implements AiScoreAdjuster {

	private static final Logger log = LoggerFactory.getLogger(DefaultAiScoreAdjuster.class);

	private final ChatClient chatClient;
	private final TemplateReader promptTemplateReader;

	public DefaultAiScoreAdjuster(ChatClient.Builder builder, TemplateReader promptTemplateReader) {
		this.chatClient = builder.build();
		this.promptTemplateReader = promptTemplateReader;
	}

	@Override
	public int adjustScore(JobPost job, ResumeProfile profile, int keywordScore) {

		String prompt = promptTemplateReader.getScoringPromptTemplate().formatted(job.getJobTitle(),
				job.getExperienceRequired(), job.getRequiredSkills(), profile.getYearsOfExperience(),
				profile.getSkills());

		try {

			String response = chatClient.prompt().user(prompt).call().content();
			return extractScore(response, keywordScore);

		} catch (Exception ex) {
			log.warn("AI scoring failed due to exception. Falling back to keyword score ({}). Error: {}", keywordScore,
					ex.getMessage());
			return keywordScore;
		}
	}

	private int extractScore(String aiResponse, int fallbackScore) {
		if (aiResponse == null || aiResponse.isBlank()) {
			log.warn("AI returned empty response. Using fallback.");
			return fallbackScore;
		}

		Pattern pattern = Pattern.compile("\\d+");
		Matcher matcher = pattern.matcher(aiResponse.trim());

		if (matcher.find()) {
			int score = Integer.parseInt(matcher.group());
			return Math.max(0, Math.min(100, score));
		} else {
			log.warn("Could not parse an integer from AI response: '{}'. Using fallback.", aiResponse);
			return fallbackScore;
		}
	}

}