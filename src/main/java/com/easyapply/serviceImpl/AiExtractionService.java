package com.easyapply.serviceImpl;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.easyapply.exception.JobExtractionException;
import com.easyapply.model.JobPost;
import com.easyapply.reader.TemplateReader;

@Service
public class AiExtractionService {

	private final ChatClient chatClient;
	private final TemplateReader promptTemplateReader;

	public AiExtractionService(ChatClient.Builder builder, TemplateReader promptTemplateReader) {
		this.chatClient = builder.build();
		this.promptTemplateReader = promptTemplateReader;
	}

	public JobPost extract(String postText) {
		if(postText == null || postText.isBlank()) return (new JobPost());
		
		String SYSTEM_PROMPT = promptTemplateReader.getAiExtractionSystemPromptTemplate();

		String userPrompt = """
				Analyze the following LinkedIn hiring post and extract the requested fields.

				POST:
				%s

				Return only a valid JSON object matching the schema defined in the system prompt.
				""".formatted(postText);

		try {
			JobPost job = chatClient.prompt().system(SYSTEM_PROMPT).user(userPrompt).call().entity(JobPost.class);
			job.setOriginalPost(postText);
			return job;
		} catch (JobExtractionException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new JobExtractionException("Failed to extract job post data", ex);
		}
	}

}
