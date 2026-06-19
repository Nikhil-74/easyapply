package com.easyapply.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumeProfile {

	private String name;
	private String yearsOfExperience;
	private String currentRole;
	private List<String> skills;
	private String summary;
}
