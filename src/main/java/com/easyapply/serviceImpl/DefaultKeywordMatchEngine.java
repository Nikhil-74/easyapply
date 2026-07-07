package com.easyapply.serviceImpl;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.easyapply.model.JobPost;
import com.easyapply.model.ResumeProfile;
import com.easyapply.service.KeywordMatchEngine;

@Component
public class DefaultKeywordMatchEngine implements KeywordMatchEngine {

	@Override
	public int calculateKeywordScore(JobPost job, ResumeProfile profile) {

		List<String> jobSkills = job.getRequiredSkills() == null ? List.of() : job.getRequiredSkills();

		List<String> resumeSkills = profile.getSkills() == null ? List.of() : profile.getSkills();

		if (jobSkills.isEmpty()) {
			return 0;
		}

		long matchedCount = jobSkills.stream().filter(skill -> skillExists(skill, resumeSkills)).count();

		return (int) Math.round((matchedCount * 100.0) / jobSkills.size());
	}

	private boolean skillExists(String jobSkill, List<String> resumeSkills) {

		String normalized = normalize(jobSkill);

		return resumeSkills.stream().map(this::normalize).anyMatch(
				skill -> skill.equals(normalized) || skill.contains(normalized) || normalized.contains(skill));
	}

	private String normalize(String value) {

		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#. ]", "").trim();
	}

	@Override
	public List<String> getMatchedSkills(JobPost job, ResumeProfile profile) {

		List<String> jobSkills = job.getRequiredSkills() == null ? List.of() : job.getRequiredSkills();

		List<String> resumeSkills = profile.getSkills() == null ? List.of() : profile.getSkills();

		return jobSkills.stream().filter(skill -> skillExists(skill, resumeSkills)).toList();
	}

	@Override
	public List<String> getMissingSkills(JobPost job, List<String> matched) {

		List<String> jobSkills = job.getRequiredSkills() == null ? List.of() : job.getRequiredSkills();

		return jobSkills.stream().filter(skill -> !matched.contains(skill)).toList();
	}

}
