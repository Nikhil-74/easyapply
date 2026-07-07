package com.easyapply.serviceImpl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.easyapply.dto.MatchScoreResult;
import com.easyapply.model.JobPost;
import com.easyapply.model.ResumeProfile;
import com.easyapply.service.AiScoreAdjuster;
import com.easyapply.service.JobMatchScorer;
import com.easyapply.service.KeywordMatchEngine;

@Service
public class DefaultJobMatchScorer implements JobMatchScorer {

	private static final Logger matchLogger = LoggerFactory.getLogger("MATCH_LOGGER");

	private static final int AI_SCORING_THRESHOLD = 40;

	private final AiScoreAdjuster aiScoreAdjuster;
	private final KeywordMatchEngine keywordMatchEngine;

	public DefaultJobMatchScorer(AiScoreAdjuster aiScoreAdjuster, KeywordMatchEngine keywordMatchEngine) {
		this.aiScoreAdjuster = aiScoreAdjuster;
		this.keywordMatchEngine = keywordMatchEngine;
	}

	@Override
	public MatchScoreResult score(JobPost job, ResumeProfile profile) {

		List<String> matched = keywordMatchEngine.getMatchedSkills(job, profile);
		List<String> missing = keywordMatchEngine.getMissingSkills(job, matched);
		int keywordScore = keywordMatchEngine.calculateKeywordScore(job, profile);

		int finalScore = keywordScore;

		if (keywordScore >= AI_SCORING_THRESHOLD) {
			finalScore = aiScoreAdjuster.adjustScore(job, profile, keywordScore);
		}

		matchLogger.info("""
				Job: {}
				Match Percentage: {}
				----------------------------------------
				""", job, finalScore);

		return MatchScoreResult.builder().score(finalScore).keywordScore(keywordScore).matchedSkills(matched)
				.missingSkills(missing).aiScored(finalScore != keywordScore).build();
	}

}