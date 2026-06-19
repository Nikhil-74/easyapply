package com.easyapply.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.easyapply.dto.BulkEmailDraft;
import com.easyapply.dto.BulkEmailDraftUpdate;
import com.easyapply.service.BulkEmailDraftService;

@RestController
@RequestMapping("/api/emails/bulk-draft")
public class BulkEmailDraftController {

	private final BulkEmailDraftService bulkEmailDraftService;

	public BulkEmailDraftController(BulkEmailDraftService bulkEmailDraftService) {
		this.bulkEmailDraftService = bulkEmailDraftService;
	}

	@GetMapping
	public BulkEmailDraft getDraft() throws IOException {
		return bulkEmailDraftService.getDraft();
	}

	@PutMapping
	public BulkEmailDraft saveDraft(@RequestBody BulkEmailDraftUpdate update) throws IOException {
		return bulkEmailDraftService.saveDraft(update);
	}

	@GetMapping("/resume")
	public ResponseEntity<InputStreamResource> getResume() throws IOException {
		Path resumePath = bulkEmailDraftService.resolveResumePath();
		if (resumePath == null || !Files.exists(resumePath)) {
			return ResponseEntity.notFound().build();
		}

		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(resumePath.getFileName().toString()).build().toString())
				.body(new InputStreamResource(Files.newInputStream(resumePath)));
	}
}
