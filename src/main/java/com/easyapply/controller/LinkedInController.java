package com.easyapply.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.easyapply.dto.JobMatchResult;
import com.easyapply.model.ResumeProfile;
import com.easyapply.serviceImpl.JobProcessorService;
import com.easyapply.serviceImpl.ResumeProfileService;

@RestController
@RequestMapping("/api/linkedin")
public class LinkedInController {

	private final JobProcessorService jobProcessorService;
	private final ResumeProfileService resumeProfileService;

	public LinkedInController(JobProcessorService jobProcessorService, ResumeProfileService resumeProfileService) {
		this.jobProcessorService = jobProcessorService;
		this.resumeProfileService = resumeProfileService;
	}

	@GetMapping("/jobs/matched")
	public List<JobMatchResult> getMatchedJobs() {
		return jobProcessorService.fetchMatchedJobs();
	}

	@GetMapping("/resume/profile")
	public ResumeProfile getResumeProfile() {
		return resumeProfileService.getProfile();
	}
}
