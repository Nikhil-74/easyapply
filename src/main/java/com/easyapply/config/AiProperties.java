package com.easyapply.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easyapply.ai")
public class AiProperties {

	private int maxPostTextLength;
	private int minExperienceYears;
	private int maxExperienceYears;
	private int matchMinimumPercentage;

	public int getMatchMinimumPercentage() {
		return matchMinimumPercentage;
	}

	public void setMatchMinimumPercentage(int matchMininumPercentage) {
		this.matchMinimumPercentage = matchMininumPercentage;
	}

	public int getMaxPostTextLength() {
		return maxPostTextLength;
	}

	public void setMaxPostTextLength(int maxPostTextLength) {
		this.maxPostTextLength = maxPostTextLength;
	}

	public int getMinExperienceYears() {
		return minExperienceYears;
	}

	public void setMinExperienceYears(int minExperienceYears) {
		this.minExperienceYears = minExperienceYears;
	}

	public int getMaxExperienceYears() {
		return maxExperienceYears;
	}

	public void setMaxExperienceYears(int maxExperienceYears) {
		this.maxExperienceYears = maxExperienceYears;
	}
}
