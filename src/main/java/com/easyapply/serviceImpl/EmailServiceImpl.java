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
import com.easyapply.reader.TemplateReader;
import com.easyapply.reader.RecruiterListReader;
import com.easyapply.service.EmailService;
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
public class EmailServiceImpl implements EmailService {

	private final UserProperties userProperties;
	private final MailProperties mailProperties;
	private final RecruiterListReader recruiterListReader;
	private final TemplateReader mailTemplateReader;
	private final ResourcePathResolver resourcePathResolver;
	private final SentEmailLogService sentEmailLogService;

	public EmailServiceImpl(
			UserProperties userProperties,
			MailProperties mailProperties,
			RecruiterListReader recruiterListReader,
			TemplateReader mailTemplateReader,
			ResourcePathResolver resourcePathResolver,
			SentEmailLogService sentEmailLogService) {
		this.userProperties = userProperties;
		this.mailProperties = mailProperties;
		this.recruiterListReader = recruiterListReader;
		this.mailTemplateReader = mailTemplateReader;
		this.resourcePathResolver = resourcePathResolver;
		this.sentEmailLogService = sentEmailLogService;
	}

	@Override
	public String sendBulkEmails() throws IOException, InterruptedException {
		validateCredentials();

		List<String[]> recipientDetails = recruiterListReader.readRecipients();
		if (recipientDetails.isEmpty()) {
			return "No recipients found.";
		}

		Session session = createMailSession();
		Path resumePath = resourcePathResolver.resolveExternalPath(userProperties.getResumePath());
		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger skippedCount = new AtomicInteger();

		ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
		List<Future<?>> futures = new ArrayList<>();

		for (String[] detail : recipientDetails) {
			if (detail.length < 2) {
				continue;
			}
			if (sentEmailLogService.wasRecentlySent(detail[0].trim())) {
				skippedCount.incrementAndGet();
				continue;
			}
			futures.add(executorService.submit(() -> sendToRecipient(session, resumePath, detail, successCount)));
		}

		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		executorService.shutdown();
		return skippedCount.get() > 0
				? String.format("%d email(s) sent successfully, %d skipped.", successCount.get(), skippedCount.get())
				: String.format("%d email(s) sent successfully.", successCount.get());
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

	private void sendToRecipient(Session session, Path resumePath, String[] detail, AtomicInteger successCount) {
		try {
			String recipientEmail = detail[0].trim();
			String recipientName = detail[1].trim();

			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress(userProperties.getEmail()));
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
			message.setSubject(mailProperties.getSubject());

			MimeBodyPart messageBodyPart = new MimeBodyPart();
			messageBodyPart.setText(mailTemplateReader.render(recipientName));

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
			sentEmailLogService.recordBulkSent(recipientEmail, recipientName, mailProperties.getSubject());
			System.out.println("Email sent successfully to: " + recipientEmail + " took " + (end - start) + " ms");
		} catch (Exception e) {
			System.out.println("Failed to send email to: " + detail[0]);
			e.printStackTrace();
		}
	}
}
