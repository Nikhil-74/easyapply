package com.easyapply.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MatchScoreResult {

    private final int score;

    private final List<String> matchedSkills;

    private final List<String> missingSkills;

    private final int keywordScore;

    private final boolean aiScored;
}