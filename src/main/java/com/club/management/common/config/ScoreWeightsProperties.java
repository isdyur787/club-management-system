package com.club.management.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 社团评分权重配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "score.weights")
public class ScoreWeightsProperties {

    /** 活动频次满分目标数量 */
    private double activityMaxCount = 12.0;

    /** 活动频次分占比 */
    private double activityWeight = 40.0;

    /** 参与率满分目标人数 */
    private double maxParticipants = 50.0;

    /** 参与率分占比 */
    private double participationWeight = 30.0;

    /** 活动多样性满分目标类型数 */
    private double maxTypeCount = 3.0;

    /** 活动多样性分占比 */
    private double diversityWeight = 20.0;

    /** 成员活跃度固定分值 */
    private double memberActivityScore = 5.0;
}
