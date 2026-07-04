package com.easyapply.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.easyapply.config.MailProperties;
import com.easyapply.dto.JobMatchResult;
import com.easyapply.model.JobPost;
import com.easyapply.model.ResumeProfile;
import com.easyapply.reader.MailTemplateReader;

@Service
public class JobProcessorService {

	private static final Logger log = LoggerFactory.getLogger(JobProcessorService.class);
	private static final int MIN_MATCH_PERCENTAGE = 60;
	private static final int AI_SCORING_THRESHOLD = 40;
	private static final String DEFAULT_RECIPIENT = "Hiring Manager";

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

	/**
	 * Fetches and filters jobs from scraped posts. Each post is extracted and
	 * validated concurrently on virtual threads since extraction/log-lookup are
	 * I/O-bound operations (network calls, AI extraction, DB/file lookups).
	 */
	public List<JobPost> fetchJobs() {
		List<String> posts = scraperService.scrapePosts();
		List<JobPost> jobs = new ArrayList<>();

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			List<Future<JobPost>> futures = posts.stream().map(post -> executor.submit(() -> processPost(post)))
					.toList();

			for (Future<JobPost> future : futures) {
				try {
					JobPost job = future.get();
					if (job != null) {
						jobs.add(job);
					}
				} catch (Exception e) {
					log.warn("Failed to process job future: {}", e.getMessage());
				}
			}
		}
		return jobs;
	}

	private JobPost processPost(String post) {
		try {
			JobPost job = aiExtractionService.extract(post);
			log.debug("Extracted job: {}", job);

			if (!aiExtractionService.matchesTargetExperience(job)) {
				return null;
			}
			if (sentEmailLogService.wasRecentlySent(job)) {
				log.info("Skipped recently emailed job: {}", job);
				return null;
			}
			log.info("Job added: {}", job);
			return job;
		} catch (Exception e) {
			log.warn("Skipped post: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Builds match results for each job. Matching involves a possible AI scoring
	 * call, so each job is scored concurrently on its own virtual thread.
	 */
	public List<JobMatchResult> fetchMatchedJobs() {
		ResumeProfile profile = resumeProfileService.getProfile();
		List<JobPost> jobs = fetchJobs();
		List<JobMatchResult> results = new ArrayList<>();

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			List<Future<JobMatchResult>> futures = jobs.stream()
					.map(job -> executor.submit(() -> buildMatchResult(job, profile))).toList();

			for (Future<JobMatchResult> future : futures) {
				try {
					JobMatchResult result = future.get();
					if (result != null) {
						results.add(result);
					}
				} catch (Exception e) {
					log.warn("Failed to match job: {}", e.getMessage());
				}
			}
		}

		results.sort(Comparator.comparingInt(JobMatchResult::getMatchPercentage).reversed());
		return results;
	}

	private JobMatchResult buildMatchResult(JobPost job, ResumeProfile profile) {
		try {
			int matchPct = calculateMatch(job, profile);
			log.info("Job {} and Match Precentage: {}", job, matchPct);
			if (matchPct < MIN_MATCH_PERCENTAGE) {
				return null;
			}

			List<String> matched = getMatchedSkills(job, profile);
			List<String> missing = getMissingSkills(job, matched);

			String recipientName = (job.getRecruiterName() == null || job.getRecruiterName().isBlank())
					? DEFAULT_RECIPIENT
					: job.getRecruiterName();

			return JobMatchResult.builder().job(job).matchPercentage(matchPct).matchedSkills(matched)
					.missingSkills(missing).emailSubject(mailProperties.getSubject())
					.emailBody(mailTemplateReader.render(recipientName)).build();
		} catch (Exception e) {
			log.warn("Failed to build match result: {}", e.getMessage());
			return null;
		}
	}

	private int calculateMatch(JobPost job, ResumeProfile profile) {
		List<String> jobSkills = job.getRequiredSkills() == null ? List.of() : job.getRequiredSkills();
		List<String> resumeSkills = profile.getSkills() == null ? List.of() : profile.getSkills();

		if (jobSkills.isEmpty()) {
			return 0;
		}

		long matchedCount = jobSkills.stream().filter(js -> skillExists(js, resumeSkills)).count();
		int jobScore = (int) Math.round((matchedCount * 100.0) / jobSkills.size());

		if (jobScore >= AI_SCORING_THRESHOLD) {
			return scoreWithAi(job, profile, jobScore);
		}

		return jobScore;
	}

	private int scoreWithAi(JobPost job, ResumeProfile profile, int fallbackScore) {
		String prompt = """
				You are a technical recruiter.

				Analyze the job requirements and candidate profile.

				Job Title: %s
				Experience Level: %s
				Required Skills: %s

				Candidate Skills: %s
				Candidate Experience: 3-4 Years

				Return ONLY a number from 0 to 100 indicating the suitability score.
				""".formatted(job.getJobTitle(), job.getExperienceRequired(), job.getRequiredSkills(),
				profile.getSkills());

		try {
			String response = chatClient.prompt().user(prompt).call().content();
			return parseScore(response);
		} catch (Exception e) {
			log.warn("AI scoring failed, falling back to keyword score: {}", e.getMessage());
			return fallbackScore;
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
			int score = Integer.parseInt(number);
			return Math.max(0, Math.min(100, score));
		} catch (NumberFormatException e) {
			// AI response contained a number too large to parse (e.g. it echoed
			// something unexpected); treat as unscored rather than crashing.
			return 0;
		}
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