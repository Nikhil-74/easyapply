package com.easyapply.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.easyapply.dto.SentEmailHistoryItem;
import com.easyapply.dto.UserProfileSettings;
import com.easyapply.dto.UserProfileSettingsUpdate;
import com.easyapply.service.SentEmailLogService;
import com.easyapply.service.UserProfileSettingsService;

@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

	private final UserProfileSettingsService settingsService;
	private final SentEmailLogService sentEmailLogService;

	public UserProfileController(UserProfileSettingsService settingsService, SentEmailLogService sentEmailLogService) {
		this.settingsService = settingsService;
		this.sentEmailLogService = sentEmailLogService;
	}

	@GetMapping("/settings")
	public UserProfileSettings getSettings() {
		return settingsService.getSettings();
	}

	@PutMapping("/settings")
	public UserProfileSettings saveSettings(@RequestBody UserProfileSettingsUpdate update) throws IOException {
		return settingsService.saveSettings(update);
	}

	@PostMapping("/resume")
	public UserProfileSettings uploadResume(@RequestParam("resume") MultipartFile resume) throws IOException {
		return settingsService.uploadResume(resume);
	}

	@GetMapping("/sent-history")
	public Map<String, List<SentEmailHistoryItem>> getSentHistory() throws IOException {
		return sentEmailLogService.getHistory();
	}
}
