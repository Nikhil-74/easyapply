package com.easyapply.service;

import com.easyapply.model.JobPost;
import com.easyapply.model.ResumeProfile;

public interface AiScoreAdjuster {

    int adjustScore(JobPost job,
                    ResumeProfile profile,
                    int keywordScore);

}