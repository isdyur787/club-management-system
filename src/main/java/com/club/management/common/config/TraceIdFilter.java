package com.club.management.common.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * TraceId 链路追踪过滤器
 *
 * 面试话术：
 * - 为每个请求生成唯一 TraceId，贯穿 Java → Python → LLM 全链路
 * - 使用 MDC (Mapped Diagnostic Context) 注入日志上下文
 * - 响应头回传 TraceId，方便前端排查问题
 */
@Slf4j
@Component
@Order(1)
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID = "traceId";
    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-]{1,64}$");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // 优先使用前端传入的 TraceId（正则校验），否则生成新的
        String traceId = req.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank() || !TRACE_ID_PATTERN.matcher(traceId).matches()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        MDC.put(TRACE_ID, traceId);
        resp.setHeader(TRACE_HEADER, traceId);

        long startTime = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("TraceId={} | {} {} | {}ms",
                    traceId, req.getMethod(), req.getRequestURI(), elapsed);
            MDC.remove(TRACE_ID);
        }
    }
}
