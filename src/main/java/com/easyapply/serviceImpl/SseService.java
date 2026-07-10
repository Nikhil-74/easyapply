package com.easyapply.serviceImpl;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.easyapply.dto.ProgressUpdate;

@Service
public class SseService {

	private volatile SseEmitter emitter;

	public SseEmitter connect() {

		emitter = new SseEmitter(Long.MAX_VALUE);

		emitter.onCompletion(() -> emitter = null);
		emitter.onTimeout(() -> emitter = null);
		emitter.onError(ex -> emitter = null);

		return emitter;
	}

	public void send(Object data) {

		if (emitter == null) {
			return;
		}

		try {
			emitter.send(SseEmitter.event().name("progress").data(data));
		} catch (Exception e) {
			emitter.completeWithError(e);
			emitter = null;
		}
	}

	public void complete() {

		ProgressUpdate finalUpdate = new ProgressUpdate(1, 1, "Matching complete. Rendering results...");
	    finalUpdate.setStatus("complete"); 
	    
	    this.send(finalUpdate);

		if (emitter != null) {
			emitter.complete();
			emitter = null;
		}
	}
}