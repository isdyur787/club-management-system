package com.club.management.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全登录配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    private Login login = new Login();

    @Data
    public static class Login {
        /** 最大登录失败次数 */
        private int maxFailCount = 5;

        /** 锁定时间（分钟） */
        private int lockMinutes = 15;
    }
}
