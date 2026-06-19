package com.easyapply.config;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeleniumConfig {

	private final SeleniumProperties seleniumProperties;

	public SeleniumConfig(SeleniumProperties seleniumProperties) {
		this.seleniumProperties = seleniumProperties;
	}

	public WebDriver createDriver() {
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--user-data-dir=" + seleniumProperties.getProfilePath());
		options.addArguments("--start-maximized");
		return new ChromeDriver(options);
	}
}
