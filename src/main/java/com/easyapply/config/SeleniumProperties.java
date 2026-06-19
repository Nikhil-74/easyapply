package com.easyapply.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easyapply.selenium")
public class SeleniumProperties {

	private String profilePath;
	private String searchUrl;
	private int maxPosts;
	private int maxScrollAttempts;
	private long scrollDelayMs;
	private long pageLoadWaitMs;
	private int elementWaitSeconds;

	public String getProfilePath() {
		return profilePath;
	}

	public void setProfilePath(String profilePath) {
		this.profilePath = profilePath;
	}

	public String getSearchUrl() {
		return searchUrl;
	}

	public void setSearchUrl(String searchUrl) {
		this.searchUrl = searchUrl;
	}

	public int getMaxPosts() {
		return maxPosts;
	}

	public void setMaxPosts(int maxPosts) {
		this.maxPosts = maxPosts;
	}

	public int getMaxScrollAttempts() {
		return maxScrollAttempts;
	}

	public void setMaxScrollAttempts(int maxScrollAttempts) {
		this.maxScrollAttempts = maxScrollAttempts;
	}

	public int getElementWaitSeconds() {
		return elementWaitSeconds;
	}

	public void setElementWaitSeconds(int elementWaitSeconds) {
		this.elementWaitSeconds = elementWaitSeconds;
	}

	public long getScrollDelayMs() {
		return scrollDelayMs;
	}

	public void setScrollDelayMs(long scrollDelayMs) {
		this.scrollDelayMs = scrollDelayMs;
	}

	public long getPageLoadWaitMs() {
		return pageLoadWaitMs;
	}

	public void setPageLoadWaitMs(long pageLoadWaitMs) {
		this.pageLoadWaitMs = pageLoadWaitMs;
	}
}
