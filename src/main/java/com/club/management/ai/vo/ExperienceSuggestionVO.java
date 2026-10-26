package com.club.management.ai.vo;

import lombok.Data;

import java.util.List;

@Data
public class ExperienceSuggestionVO {

    private Boolean hasHistory;

    private String experienceSummary;

    private List<String> suggestions;

    private List<String> riskWarnings;
}
