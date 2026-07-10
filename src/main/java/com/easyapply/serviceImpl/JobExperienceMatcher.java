package com.easyapply.serviceImpl;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.easyapply.config.AiProperties;
import com.easyapply.model.JobPost;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JobExperienceMatcher {

	private final AiProperties aiProperties;

	private static final Pattern RANGE_PATTERN = Pattern
			.compile("(\\d+(?:\\.\\d+)?)\\s*(?:-|to)\\s*(\\d+(?:\\.\\d+)?)");

	private static final Pattern PLUS_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*\\+");

	private static final Pattern SINGLE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

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
