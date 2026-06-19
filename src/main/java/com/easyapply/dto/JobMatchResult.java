package com.easyapply.dto;

import java.util.List;

import com.easyapply.model.JobPost;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchResult {

	private JobPost job;
	private int matchPercentage;
	private List<String> matchedSkills;
	private List<String> missingSkills;
	private String emailSubject;
	private String emailBody;
}
