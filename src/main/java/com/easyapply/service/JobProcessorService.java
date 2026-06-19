package com.easyapply.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.easyapply.config.MailProperties;
import com.easyapply.dto.JobMatchResult;
import com.easyapply.model.JobPost;
import com.easyapply.model.ResumeProfile;
import com.easyapply.reader.MailTemplateReader;

@Service
public class JobProcessorService {

	private final LinkedInScraperService scraperService;
	private final AiExtractionService aiExtractionService;
	private final ResumeProfileService resumeProfileService;
	private final SentEmailLogService sentEmailLogService;
	private final MailProperties mailProperties;
	private final MailTemplateReader mailTemplateReader;
	private final ChatClient chatClient;
	
	public JobProcessorService(LinkedInScraperService scraperService, AiExtractionService aiExtractionService,
			ResumeProfileService resumeProfileService, SentEmailLogService sentEmailLogService,
			MailProperties mailProperties, MailTemplateReader mailTemplateReader, ChatClient.Builder chatClient) {
		this.scraperService = scraperService;
		this.aiExtractionService = aiExtractionService;
		this.resumeProfileService = resumeProfileService;
		this.sentEmailLogService = sentEmailLogService;
		this.mailProperties = mailProperties;
		this.mailTemplateReader = mailTemplateReader;
		this.chatClient = chatClient.build();
	}

	public List<JobPost> fetchJobs() {
		List<String> posts = scraperService.scrapePosts();
		List<JobPost> jobs = new ArrayList<>();

		for (String post : posts) {
			try {
				JobPost job = aiExtractionService.extract(post);
				System.err.println("Job: " + job);
				if (aiExtractionService.matchesTargetExperience(job)) {
					if (sentEmailLogService.wasRecentlySent(job)) {
						System.out.println("Skipped recently emailed job: " + job);
						continue;
					}
					jobs.add(job);
					System.out.println("Job added: " + job);
				}
			} catch (Exception e) {
				System.err.println("Skipped post: " + e.getMessage());
			}
		}
		return jobs;
	}

	public List<JobMatchResult> fetchMatchedJobs() {
		ResumeProfile profile = resumeProfileService.getProfile();
		List<JobPost> jobs = fetchJobs();
		List<JobMatchResult> results = new ArrayList<>();

		for (JobPost job : jobs) {
			try {
				int matchPct = calculateMatch(job, profile);
				if (matchPct < 70)
					continue;

				List<String> matched = getMatchedSkills(job, profile);
				List<String> missing = getMissingSkills(job, matched);

				String recipientName = job.getRecruiterName() == null || job.getRecruiterName().isBlank()
						? "Hiring Manager"
						: job.getRecruiterName();

				results.add(JobMatchResult.builder().job(job).matchPercentage(matchPct).matchedSkills(matched)
						.missingSkills(missing).emailSubject(mailProperties.getSubject())
						.emailBody(mailTemplateReader.render(recipientName)).build());
			} catch (Exception e) {
				System.err.println("Failed to match job: " + e.getMessage());
			}
		}

		results.sort(Comparator.comparingInt(JobMatchResult::getMatchPercentage).reversed());
		return results;
	}

	private int calculateMatch(JobPost job, ResumeProfile profile) {
		List<String> jobSkills = job.getRequiredSkills() == null ? List.of() : job.getRequiredSkills();
		List<String> resumeSkills = profile.getSkills() == null ? List.of() : profile.getSkills();


		long matched = jobSkills.stream().filter(js -> skillExists(js, resumeSkills)).count();
		int jobScore = (int) Math.round((matched * 100.0) / jobSkills.size());
		if (jobScore >= 40) {
			String prompt = """
					You are a technical recruiter.

					Analyze the job requirements and candidate profile.

					Job Title: %s
					Experience Level: %s
					Required Skills: %s

					Candidate Skills: %s
					Candidate Experience: 3-4 Years

					Return ONLY a number from 0 to 100 indicating the suitability score.
					""".formatted(job.getJobTitle(), job.getExperienceRequired(), job.getRequiredSkills(), profile.getSkills());

			try {
				String response = chatClient.prompt().user(prompt).call().content();

				return parseScore(response);

			} catch (Exception e) {
				return jobScore;
			}
		}
		
		return jobScore;
	}

	private int parseScore(String response) {

		String number = response.replaceAll("[^0-9]", "");

		if (number.isBlank()) {
			return 0;
		}

		int score = Integer.parseInt(number);

		return Math.max(0, Math.min(100, score));
	}

	private List<String> getMatchedSkills(JobPost job, ResumeProfile profile) {
		List<String> jobSkills = job.getRequiredSkills() == null ? List.of() : job.getRequiredSkills();
		List<String> resumeSkills = profile.getSkills() == null ? List.of() : profile.getSkills();
		return jobSkills.stream().filter(js -> skillExists(js, resumeSkills)).toList();
	}

	private List<String> getMissingSkills(JobPost job, List<String> matched) {
		List<String> jobSkills = job.getRequiredSkills() == null ? List.of() : job.getRequiredSkills();
		return jobSkills.stream().filter(skill -> !matched.contains(skill)).toList();
	}

	private boolean skillExists(String jobSkill, List<String> resumeSkills) {
		String normalized = normalize(jobSkill);
		return resumeSkills.stream().map(this::normalize)
				.anyMatch(rs -> rs.equals(normalized) || rs.contains(normalized) || normalized.contains(rs));
	}

	private String normalize(String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#. ]", "").trim();
	}


}
