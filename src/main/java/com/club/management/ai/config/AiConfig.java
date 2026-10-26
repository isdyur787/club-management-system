package com.club.management.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.service")
public class AiConfig {

    private String baseUrl;

    private String internalKey;

    private int timeout = 60000;

    @Bean
    public RestTemplate aiRestTemplate() {
        // 重试逻辑由 AiServiceImpl.retryPostForEntity 承担，此处仅配置超时
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        RestTemplate template = new RestTemplate(factory);
        template.getInterceptors().add((request, body, execution) -> {
            if (internalKey != null && !internalKey.isBlank()) {
                request.getHeaders().set("X-Internal-Key", internalKey);
            }
            return execution.execute(request, body);
        });
        return template;
    }
}
