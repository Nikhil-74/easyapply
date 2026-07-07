package com.easyapply.service;

import java.util.List;

import com.easyapply.model.JobPost;
import com.easyapply.model.ResumeProfile;

public interface KeywordMatchEngine {

    int calculateKeywordScore(JobPost job, ResumeProfile profile);

    List<String> getMatchedSkills(JobPost job, ResumeProfile profile);

    List<String> getMissingSkills(JobPost job, List<String> matched);
}