package com.easyapply.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.easyapply.serviceImpl.SseService;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

	@Autowired
	private SseService sseService;

	@GetMapping
	public SseEmitter progress() {
		return sseService.connect();
	}
}