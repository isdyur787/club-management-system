package com.club.management.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 积分规则配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "points")
public class PointsProperties {

    /** 建议被采纳 */
    private int adoptedSuggestion = 5;

    /** 签到成功 */
    private int signIn = 1;

    /** 提交活动心得 */
    private int checkinReflection = 2;

    /** 打卡含照片 */
    private int checkinPhoto = 1;

    /** 提交打卡建议 */
    private int checkinSuggestion = 1;

    /** 签退成功 */
    private int signOut = 1;
}
