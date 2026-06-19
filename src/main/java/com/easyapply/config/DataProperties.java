package com.easyapply.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "easyapply.data")
public class DataProperties {

	private String recruiterList = "classpath:data/recruiter.txt";
	private String mailTemplate = "classpath:templates/mail-template.txt";

	public String getRecruiterList() {
		return recruiterList;
	}

	public void setRecruiterList(String recruiterList) {
		this.recruiterList = recruiterList;
	}

	public String getMailTemplate() {
		return mailTemplate;
	}

	public void setMailTemplate(String mailTemplate) {
		this.mailTemplate = mailTemplate;
	}
}
