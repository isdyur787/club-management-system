package com.club.management.ai.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ChatResponseVO {

    private String sessionId;

    private String answer;

    private List<Map<String, String>> sources;
}
