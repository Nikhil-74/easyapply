package com.easyapply.serviceImpl;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.easyapply.config.MailProperties;
import com.easyapply.config.UserProperties;
import com.easyapply.dto.JobMatchResult;
import com.easyapply.model.JobPost;
import com.easyapply.util.ResourcePathResolver;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

@Service
public class MatchedJobEmailService {

	private final UserProperties userProperties;
	private final MailProperties mailProperties;
	private final ResourcePathResolver resourcePathResolver;
	private final SentEmailLogService sentEmailLogService;

	public MatchedJobEmailService(
			UserProperties userProperties,
			MailProperties mailProperties,
			ResourcePathResolver resourcePathResolver,
			SentEmailLogService sentEmailLogService) {
		this.userProperties = userProperties;
		this.mailProperties = mailProperties;
		this.resourcePathResolver = resourcePathResolver;
		this.sentEmailLogService = sentEmailLogService;
	}

	public String sendShortlistedJobEmails(List<JobMatchResult> matchedJobs) throws IOException, InterruptedException {
		validateCredentials();

		if (matchedJobs == null || matchedJobs.isEmpty()) {
			return "No shortlisted jobs found.";
		}

		List<MatchedEmailTask> tasks = buildEmailTasks(matchedJobs);
		if (tasks.isEmpty()) {
			return "No recipient emails found in matched jobs.";
		}

		Session session = createMailSession();
		Path resumePath = resourcePathResolver.resolveExternalPath(userProperties.getResumePath());
		AtomicInteger successCount = new AtomicInteger();

		ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
		List<Future<?>> futures = new ArrayList<>();

		for (MatchedEmailTask task : tasks) {
			futures.add(executorService.submit(() -> sendToRecipient(session, resumePath, task, successCount)));
		}

		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (Exception e) {
					e.printStackTrace();
			}
		}

		executorService.shutdown();
		return successCount.get() + " email(s) sent successfully for " + matchedJobs.size() + " shortlisted job(s).";
	}

	private List<MatchedEmailTask> buildEmailTasks(List<JobMatchResult> matchedJobs) {
		List<MatchedEmailTask> tasks = new ArrayList<>();

		for (JobMatchResult match : matchedJobs) {
			if (match.getJob() == null || match.getJob().getContactEmails() == null) {
				continue;
			}

			String subject = match.getEmailSubject();
			String body = match.getEmailBody();
			if (subject == null || subject.isBlank() || body == null || body.isBlank()) {
				continue;
			}

			for (String email : match.getJob().getContactEmails()) {
				if (email != null && !email.isBlank()) {
					tasks.add(new MatchedEmailTask(match.getJob(), email.trim(), subject, body));
				}
			}
		}

		return tasks;
	}

	private void validateCredentials() {
		if (userProperties.getEmail() == null || userProperties.getEmail().isBlank()) {
			throw new IllegalStateException(
					"User email is not configured. Set EASYAPPLY_USER_EMAIL or easyapply.user.email.");
		}
		if (userProperties.getPassword() == null || userProperties.getPassword().isBlank()) {
			throw new IllegalStateException(
					"User password is not configured. Set EASYAPPLY_USER_PASSWORD or easyapply.user.password.");
		}
	}

	private Session createMailSession() {
		Properties properties = new Properties();
		properties.put("mail.smtp.host", mailProperties.getSmtp().getHost());
		properties.put("mail.smtp.port", String.valueOf(mailProperties.getSmtp().getPort()));
		properties.put("mail.smtp.auth", "true");
		properties.put("mail.smtp.starttls.enable", "true");

		return Session.getInstance(properties, new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(userProperties.getEmail(), userProperties.getPassword());
			}
		});
	}

	private void sendToRecipient(
			Session session, Path resumePath, MatchedEmailTask task, AtomicInteger successCount) {
		try {
			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress(userProperties.getEmail()));
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(task.recipientEmail()));
			message.setSubject(task.subject());

			MimeBodyPart messageBodyPart = new MimeBodyPart();
			messageBodyPart.setText(task.body());

			MimeBodyPart attachmentPart = new MimeBodyPart();
			attachmentPart.attachFile(resumePath.toFile());

			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(messageBodyPart);
			multipart.addBodyPart(attachmentPart);
			message.setContent(multipart);

			long start = System.currentTimeMillis();
			Transport.send(message);
			long end = System.currentTimeMillis();

			successCount.incrementAndGet();
			sentEmailLogService.recordSent(task.job(), task.recipientEmail(), task.subject());
			System.out.println(
					"Matched job email sent to: " + task.recipientEmail() + " took " + (end - start) + " ms");
		} catch (Exception e) {
			System.out.println("Failed to send matched job email to: " + task.recipientEmail());
			e.printStackTrace();
		}
	}

	private record MatchedEmailTask(JobPost job, String recipientEmail, String subject, String body) {
	}
}
