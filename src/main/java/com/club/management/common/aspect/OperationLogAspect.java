package com.club.management.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.club.management.common.annotation.OperationLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.UUID;

@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "");

        // Skip MultipartFile parameters for JSON serialization
        String params;
        if (operationLog.saveRequestData()) {
            Object[] safeArgs = Arrays.stream(point.getArgs())
                    .map(arg -> arg instanceof MultipartFile ? "[MultipartFile]" : arg)
                    .toArray();
            try {
                params = objectMapper.writeValueAsString(safeArgs);
            } catch (Exception e) {
                params = Arrays.toString(safeArgs);
            }
        } else {
            params = "***";
        }

        log.info("[{}] 请求开始 | module={} | operation={} | method={} | params={}",
                traceId,
                operationLog.module(),
                operationLog.operation(),
                point.getSignature().toShortString(),
                params);

        Object result;
        try {
            result = point.proceed();
        } catch (Exception e) {
            log.error("[{}] 请求异常 | module={} | operation={} | error={}",
                    traceId,
                    operationLog.module(),
                    operationLog.operation(),
                    e.getMessage());
            throw e;
        }

        long costTime = System.currentTimeMillis() - startTime;
        log.info("[{}] 请求完成 | module={} | operation={} | cost={}ms",
                traceId,
                operationLog.module(),
                operationLog.operation(),
                costTime);

        return result;
    }
}
