package com.easyapply.serviceImpl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.easyapply.dto.SentEmailHistoryItem;
import com.easyapply.model.JobPost;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SentEmailLogService {

	private static final String LOG_FILE_NAME = "sent-emails.json";
	private static final int LOOKBACK_DAYS = 2;

	private final ObjectMapper objectMapper;
	private final Path logPath;

	public SentEmailLogService(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.logPath = Paths.get(System.getProperty("user.dir")).resolve(LOG_FILE_NAME);
	}

	public boolean wasRecentlySent(JobPost job) {
		if (job == null) {
			return false;
		}

		Set<String> recentKeys = readRecentSentKeys();
		return recentKeys.contains(jobKey(job));
	}
	
	public boolean wasRecentlySent(String recipient) {
	    return readRecentSentKeys().stream()
	            .anyMatch(key -> key.startsWith(recipient + "|"));
	}

	public synchronized void recordSent(JobPost job, String recipientEmail, String subject) throws IOException {
		if (job == null) {
			return;
		}

		Map<String, List<SentEmailLogEntry>> log = readLog();
		String today = LocalDate.now().toString();
		List<SentEmailLogEntry> entries = log.computeIfAbsent(today, ignored -> new ArrayList<>());
		entries.add(new SentEmailLogEntry(
				OffsetDateTime.now().toString(),
				jobKey(job),
				job.getJobTitle(),
				job.getRecruiterName(),
				job.getRecruiterProfile(),
				job.getExperienceRequired(),
				job.getLocation(),
				job.getContactEmails(),
				recipientEmail,
				subject));
		writeLog(log);
	}

	public synchronized void recordBulkSent(String recipientEmail, String recipientName, String subject) throws IOException {
		Map<String, List<SentEmailLogEntry>> log = readLog();
		String today = LocalDate.now().toString();
		List<SentEmailLogEntry> entries = log.computeIfAbsent(today, ignored -> new ArrayList<>());
		entries.add(new SentEmailLogEntry(
				OffsetDateTime.now().toString(),
				"",
				"Bulk outreach",
				recipientName,
				"",
				"",
				List.of(),
				List.of(recipientEmail),
				recipientEmail,
				subject));
		writeLog(log);
	}

	public Map<String, List<SentEmailHistoryItem>> getHistory() throws IOException {
		Map<String, List<SentEmailLogEntry>> log = readLog();
		Map<String, List<SentEmailHistoryItem>> history = new LinkedHashMap<>();

		log.entrySet().stream()
				.sorted(Map.Entry.<String, List<SentEmailLogEntry>>comparingByKey().reversed())
				.forEach(entry -> history.put(entry.getKey(), entry.getValue().stream()
						.map(item -> new SentEmailHistoryItem(
								item.sentAt(),
								item.jobTitle(),
								item.recruiterName(),
								item.recipientEmail(),
								item.emailSubject(),
								item.contactEmails()))
						.toList()));

		return history;
	}

	private Set<String> readRecentSentKeys() {
		Map<String, List<SentEmailLogEntry>> log;
		try {
			log = readLog();
		} catch (IOException ex) {
			System.err.println("Could not read sent email log: " + ex.getMessage());
			return Set.of();
		}

		Set<String> keys = new HashSet<>();
		LocalDate today = LocalDate.now();
		for (int offset = 0; offset <= LOOKBACK_DAYS; offset++) {
			List<SentEmailLogEntry> entries = log.get(today.minusDays(offset).toString());
			if (entries == null) {
				continue;
			}

			for (SentEmailLogEntry entry : entries) {
				if (entry.jobKey() != null && !entry.jobKey().isBlank()) {
					keys.add(entry.jobKey());
				}
			}
		}
		return keys;
	}

	private synchronized Map<String, List<SentEmailLogEntry>> readLog() throws IOException {
		if (!Files.exists(logPath)) {
			return new LinkedHashMap<>();
		}

		String content = Files.readString(logPath, StandardCharsets.UTF_8);
		if (content.isBlank()) {
			return new LinkedHashMap<>();
		}

		return objectMapper.readValue(content, new TypeReference<LinkedHashMap<String, List<SentEmailLogEntry>>>() {
		});
	}

	private void writeLog(Map<String, List<SentEmailLogEntry>> log) throws IOException {
		Files.writeString(
				logPath,
				objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(log) + System.lineSeparator(),
				StandardCharsets.UTF_8);
	}

	private String jobKey(JobPost job) {
		String emails = normalizeList(job.getContactEmails());
		String title = normalize(job.getJobTitle());
		String recruiterProfile = normalize(job.getRecruiterProfile());

		if (!emails.isBlank()) {
			return emails + "|" + title;
		}

		if (!recruiterProfile.isBlank()) {
			return recruiterProfile + "|" + title;
		}

		return title + "|" + normalize(job.getRecruiterName()) + "|" + normalizeList(job.getLocation());
	}

	private String normalizeList(List<String> values) {
		if (values == null) {
			return "";
		}

		return values.stream()
				.filter(Objects::nonNull)
				.map(this::normalize)
				.filter(value -> !value.isBlank())
				.sorted()
				.reduce((left, right) -> left + "," + right)
				.orElse("");
	}

	private String normalize(String value) {
		return Objects.toString(value, "")
				.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9@.+# ]", " ")
				.replaceAll("\\s+", " ")
				.trim();
	}

	private record SentEmailLogEntry(
			String sentAt,
			String jobKey,
			String jobTitle,
			String recruiterName,
			String recruiterProfile,
			String experienceRequired,
			List<String> location,
			List<String> contactEmails,
			String recipientEmail,
			String emailSubject) {
	}
}
