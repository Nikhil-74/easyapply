package com.easyapply.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import com.easyapply.config.SeleniumConfig;
import com.easyapply.config.SeleniumProperties;
import com.easyapply.exception.ScrapingException;

@Service
public class LinkedInScraperService {

	private static final String POST_SELECTOR = "div[role='listitem']";

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final SeleniumConfig seleniumConfig;
	private final SeleniumProperties seleniumProperties;

	public LinkedInScraperService(SeleniumConfig seleniumConfig, SeleniumProperties seleniumProperties) {
		this.seleniumConfig = seleniumConfig;
		this.seleniumProperties = seleniumProperties;
	}

	public List<String> scrapePosts() {
		int target = seleniumProperties.getMaxPosts();
		List<String> posts = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		WebDriver driver = seleniumConfig.createDriver();
		try {
			driver.get(seleniumProperties.getSearchUrl());
			Thread.sleep(seleniumProperties.getPageLoadWaitMs());

			WebDriverWait wait = new WebDriverWait(driver,
					Duration.ofSeconds(seleniumProperties.getElementWaitSeconds()));

			for (int scroll = 0; scroll < seleniumProperties.getMaxScrollAttempts()
					&& posts.size() < target; scroll++) {
				wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(POST_SELECTOR)));
				collectPosts(driver, posts, seen, target);

				System.out.println("Scroll " + (scroll + 1) + ": " + posts.size() + " / " + target + " posts");

				if (posts.size() >= target) {
					break;
				}

				scrollPage(driver);
				Thread.sleep(seleniumProperties.getScrollDelayMs());
			}

			if (posts.isEmpty()) {
				throw new ScrapingException("No posts found. Check LinkedIn login and search URL.");
			}

			return posts.size() > target ? posts.subList(0, target) : posts;
		} catch (ScrapingException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new ScrapingException("Scraping failed", ex);
		} finally {
			driver.quit();
		}
	}
	
	private void collectPosts(WebDriver driver, List<String> posts, Set<String> seen, int target) {
		for (WebElement post : driver.findElements(By.cssSelector(POST_SELECTOR))) {
			if (posts.size() >= target) {
				return;
			}
			try {
				String text = post.getText().trim();
				List<String> emails = extractEmails(post);

				if (!emails.isEmpty()) {
					text += "\n\nEmails Found:\n" + String.join(", ", emails);
				}

				if (text.length() < 20 || !seen.add(text)) {
					continue;
				}
				posts.add(text);
			} catch (StaleElementReferenceException ignored) {
				// element went stale while scrolling — skip it
			}
		}
	}

	private List<String> extractEmails(WebElement post) {

		Set<String> emails = new LinkedHashSet<>();

		post.findElements(By.cssSelector("a[href^='mailto:']")).stream().map(e -> e.getDomAttribute("href"))
				.filter(Objects::nonNull).map(href -> href.replaceFirst("mailto:", "").split("\\?")[0])
				.forEach(emails::add);

		String text = post.getDomAttribute("textContent");

		if (text != null) {
			text = text.replaceAll("\\[at\\]", "@").replaceAll("\\(at\\)", "@");
			Matcher matcher = EMAIL_PATTERN.matcher(text);
			while (matcher.find()) {
				emails.add(matcher.group().toLowerCase());
			}
		}
//		if(emails.size() > 0)
//			System.out.println("Found emails: " + emails);

		return new ArrayList<>(emails);
	}

	private void scrollPage(WebDriver driver) {
		JavascriptExecutor js = (JavascriptExecutor) driver;

		// LinkedIn infinite scroll lives inside a feed container, not the window
		js.executeScript("""
				const items = document.querySelectorAll("div[role='listitem']");
				if (items.length > 0) {
				    items[items.length - 1].scrollIntoView({block: 'end', behavior: 'instant'});
				}
				const feed = document.querySelector('.scaffold-finite-scroll__content')
				          || document.querySelector('.scaffold-finite-scroll')
				          || document.querySelector('main.scaffold-layout__main')
				          || document.querySelector('main');
				if (feed) {
				    feed.scrollTop = feed.scrollHeight;
				}
				""");

		// keyboard scroll as backup
		WebElement body = driver.findElement(By.tagName("body"));
		new Actions(driver).click(body).sendKeys(Keys.END).sendKeys(Keys.PAGE_DOWN).perform();
	}
}
