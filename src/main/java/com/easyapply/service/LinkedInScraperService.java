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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.easyapply.config.SeleniumConfig;
import com.easyapply.config.SeleniumProperties;
import com.easyapply.exception.ScrapingException;

@Service
public class LinkedInScraperService {

	private static final Logger log = LoggerFactory.getLogger(LinkedInScraperService.class);

	private static final String POST_SELECTOR = "div[role='listitem']";
	private static final int MIN_POST_TEXT_LENGTH = 20;

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final Pattern AT_BRACKET_PATTERN = Pattern.compile("\\[at\\]", Pattern.CASE_INSENSITIVE);
	private static final Pattern AT_PAREN_PATTERN = Pattern.compile("\\(at\\)", Pattern.CASE_INSENSITIVE);

	private static final String SCROLL_INTO_VIEW_SCRIPT = """
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
			""";

	private final SeleniumConfig seleniumConfig;
	private final SeleniumProperties seleniumProperties;

	public LinkedInScraperService(SeleniumConfig seleniumConfig, SeleniumProperties seleniumProperties) {
		this.seleniumConfig = seleniumConfig;
		this.seleniumProperties = seleniumProperties;
	}

	/**
	 * Scrapes up to {@code maxPosts} unique post texts from the configured search
	 * URL, scrolling the feed as needed.
	 *
	 * <p>
	 * Note: this method drives a single Selenium {@link WebDriver} session
	 * sequentially. WebDriver sessions are not thread-safe, so this work is
	 * intentionally NOT parallelized across virtual threads — each command (find,
	 * getText, execute script) is itself a blocking round-trip to the browser
	 * driver, but they must happen in order against the one session.
	 */
	public List<String> scrapePosts() {
		int target = seleniumProperties.getMaxPosts();
		List<String> posts = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		WebDriver driver = seleniumConfig.createDriver();
		try {
			driver.get(seleniumProperties.getSearchUrl());
			sleep(seleniumProperties.getPageLoadWaitMs());

			WebDriverWait wait = new WebDriverWait(driver,
					Duration.ofSeconds(seleniumProperties.getElementWaitSeconds()));

			for (int scroll = 0; scroll < seleniumProperties.getMaxScrollAttempts()
					&& posts.size() < target; scroll++) {
				wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(POST_SELECTOR)));
				collectPosts(driver, posts, seen, target);

				log.info("Scroll {}: {} / {} posts", scroll + 1, posts.size(), target);

				if (posts.size() >= target) {
					break;
				}

				scrollPage(driver);
				sleep(seleniumProperties.getScrollDelayMs());
			}

			if (posts.isEmpty()) {
				throw new ScrapingException("No posts found. Check LinkedIn login and search URL.");
			}

			return posts.size() > target ? posts.subList(0, target) : posts;
		} catch (ScrapingException ex) {
			throw ex;
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ScrapingException("Scraping interrupted", ex);
		} catch (Exception ex) {
			throw new ScrapingException("Scraping failed", ex);
		} finally {
			driver.quit();
		}
	}

	private void sleep(long millis) throws InterruptedException {
		if (millis > 0) {
			Thread.sleep(millis);
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

				if (text.length() < MIN_POST_TEXT_LENGTH || !seen.add(text)) {
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
			text = AT_BRACKET_PATTERN.matcher(text).replaceAll("@");
			text = AT_PAREN_PATTERN.matcher(text).replaceAll("@");
			Matcher matcher = EMAIL_PATTERN.matcher(text);
			while (matcher.find()) {
				emails.add(matcher.group().toLowerCase());
			}
		}

		if (!emails.isEmpty()) {
			log.debug("Found emails: {}", emails);
		}

		return new ArrayList<>(emails);
	}

	private void scrollPage(WebDriver driver) {
		JavascriptExecutor js = (JavascriptExecutor) driver;

		// LinkedIn infinite scroll lives inside a feed container, not the window
		js.executeScript(SCROLL_INTO_VIEW_SCRIPT);

		// keyboard scroll as backup
		WebElement body = driver.findElement(By.tagName("body"));
		new Actions(driver).click(body).sendKeys(Keys.END).sendKeys(Keys.PAGE_DOWN).perform();
	}
}