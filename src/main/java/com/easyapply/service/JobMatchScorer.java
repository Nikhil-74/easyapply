package com.easyapply.service;

import com.easyapply.dto.MatchScoreResult;
import com.easyapply.model.JobPost;
import com.easyapply.model.ResumeProfile;

public interface JobMatchScorer {

	MatchScoreResult score(JobPost job, ResumeProfile profile);

}