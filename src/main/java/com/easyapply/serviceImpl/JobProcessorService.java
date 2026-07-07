package com.easyapply.serviceImpl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.easyapply.config.MailProperties;
import com.easyapply.dto.JobMatchResult;
import com.easyapply.dto.MatchScoreResult;
import com.easyapply.model.JobPost;
import com.easyapply.model.ResumeProfile;
import com.easyapply.reader.MailTemplateReader;
import com.easyapply.service.JobMatchScorer;

@Service
public class JobProcessorService {

	private static final Logger log = LoggerFactory.getLogger(JobProcessorService.class);
	private static final int MIN_MATCH_PERCENTAGE = 60;
	private static final String DEFAULT_RECIPIENT = "Hiring Manager";

	private final LinkedInScraperService scraperService;
	private final AiExtractionService aiExtractionService;
	private final ResumeProfileService resumeProfileService;
	private final SentEmailLogService sentEmailLogService;
	private final MailProperties mailProperties;
	private final MailTemplateReader mailTemplateReader;
	private final JobMatchScorer jobMatchScorer;

	public JobProcessorService(LinkedInScraperService scraperService, AiExtractionService aiExtractionService,
			ResumeProfileService resumeProfileService, SentEmailLogService sentEmailLogService,
			MailProperties mailProperties, MailTemplateReader mailTemplateReader, JobMatchScorer jobMatchScorer) {
		this.scraperService = scraperService;
		this.aiExtractionService = aiExtractionService;
		this.resumeProfileService = resumeProfileService;
		this.sentEmailLogService = sentEmailLogService;
		this.mailProperties = mailProperties;
		this.mailTemplateReader = mailTemplateReader;
		this.jobMatchScorer = jobMatchScorer;
	}

	public List<JobMatchResult> fetchMatchedJobs() {
		ResumeProfile profile = resumeProfileService.getProfile();
		List<String> posts = scraperService.scrapePosts();
		List<JobMatchResult> results = new ArrayList<>();

		int total = posts.size();
		int completed = 0;

		for (String post : posts) {
			completed++;
			try {
				JobPost job = processPost(post);
				if (job != null) {
					JobMatchResult result = buildMatchResult(job, profile);
					if (result != null) {
						results.add(result);
					}
				}
			} catch (Exception e) {
				log.warn("Failed to match job: {}", e.getMessage());
			} finally {
				log.info("Processed {}/{} jobs ({} remaining)", completed, total, total - completed);
			}
		}

		results.sort(Comparator.comparingInt(JobMatchResult::getMatchPercentage).reversed());
		return results;
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
			return job;
		} catch (Exception e) {
			log.warn("Skipped post: {}", e.getMessage());
			return null;
		}
	}

	private JobMatchResult buildMatchResult(JobPost job, ResumeProfile profile) {
		try {
			MatchScoreResult scoreResult = jobMatchScorer.score(job, profile);

			int matchPct = scoreResult.getScore();

			if (matchPct < MIN_MATCH_PERCENTAGE) {
				return null;
			}

			String recipientName = (job.getRecruiterName() == null || job.getRecruiterName().isBlank())
					? DEFAULT_RECIPIENT
					: extractFirstName(job.getRecruiterName());

			return JobMatchResult.builder().job(job).matchPercentage(matchPct)
					.matchedSkills(scoreResult.getMatchedSkills()).missingSkills(scoreResult.getMissingSkills())
					.emailSubject(mailProperties.getSubject()).emailBody(mailTemplateReader.render(recipientName))
					.build();

		} catch (Exception e) {
			log.warn("Failed to build match result: {}", e.getMessage(), e);
			return null;
		}
	}

	private String extractFirstName(String recruiterName) {
		String firstName = recruiterName.trim().split("\\s+")[0];
		if (firstName.isEmpty()) {
			return firstName;
		}
		return Character.toUpperCase(firstName.charAt(0)) + firstName.substring(1);
	}

}