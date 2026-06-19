package com.easyapply.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobPost {

//	private String company;
//	private String recruiter;
//	private String experienceLevel;
//	private String hiringManager;
//	private String jobTitle;
//	private List<String> skills;
//	private List<String> emails;
	
	private String jobTitle;
    private String recruiterName;
    private String recruiterProfile;
    private String experienceRequired;
    private List<String> requiredSkills;
    private List<String> location;
    private List<String> contactEmails;

}
