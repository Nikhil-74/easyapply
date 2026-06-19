package com.easyapply.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.easyapply.dto.JobMatchResult;
import com.easyapply.service.EmailService;
import com.easyapply.service.MatchedJobEmailService;

@RestController
@RequestMapping("/api/emails")
public class EmailController {

	private final EmailService emailService;
	private final MatchedJobEmailService matchedJobEmailService;

	public EmailController(EmailService emailService, MatchedJobEmailService matchedJobEmailService) {
		this.emailService = emailService;
		this.matchedJobEmailService = matchedJobEmailService;
	}

	@PostMapping("/send")
	public String sendEmails() throws Exception {
		return emailService.sendBulkEmails();
	}

	@PostMapping("/send-shortlisted")
	public String sendShortlistedJobEmails(@RequestBody List<JobMatchResult> shortlistedJobs) throws Exception {
		return matchedJobEmailService.sendShortlistedJobEmails(shortlistedJobs);
	}
}
