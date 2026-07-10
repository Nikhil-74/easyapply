package com.easyapply.serviceImpl;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.easyapply.dto.ProgressUpdate;

@Service
public class SseService {

	private volatile SseEmitter emitter;

	public SseEmitter connect() {
		SseEmitter newEmitter = new SseEmitter(Long.MAX_VALUE);

		newEmitter.onCompletion(() -> this.emitter = null);
		newEmitter.onTimeout(() -> this.emitter = null);
		newEmitter.onError(ex -> this.emitter = null);

		this.emitter = newEmitter;

		return newEmitter;
	}

	public void send(Object data) {
		sendEvent("progress", data);
	}

	public void log(String message) {
		sendEvent("log", message);
	}

	private void sendEvent(String eventName, Object payload) {

		SseEmitter currentEmitter = this.emitter;

		if (currentEmitter == null) {
			return;
		}

		try {
			currentEmitter.send(SseEmitter.event().name(eventName).data(payload));
		} catch (Exception e) {
			currentEmitter.completeWithError(e);
			this.emitter = null;
		}
	}

	public void complete() {

		ProgressUpdate finalUpdate = new ProgressUpdate(1, 1, "Matching complete. Rendering results...");
		finalUpdate.setStatus("complete");
		this.send(finalUpdate);

		SseEmitter currentEmitter = this.emitter;

		if (currentEmitter != null) {
			try {
				currentEmitter.complete();
			} catch (Exception ignored) {
				// Safely ignore if the connection dropped exactly as it completed
			} finally {
				this.emitter = null;
			}
		}
	}
}