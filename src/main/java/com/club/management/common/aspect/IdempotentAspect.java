package com.club.management.common.aspect;

import com.club.management.common.annotation.Idempotent;
import com.club.management.common.config.RedisKeyConstants;
import com.club.management.common.exception.ConflictException;
import com.club.management.common.exception.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class IdempotentAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint point, Idempotent idempotent) throws Throwable {
        String key = generateKey(point, idempotent);
        String redisKey = RedisKeyConstants.IDEMPOTENT + key;

        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", idempotent.expireSeconds(), TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(success)) {
            throw new ConflictException(ErrorCode.IDEMPOTENT_CONFLICT, idempotent.message());
        }

        try {
            return point.proceed();
        } catch (Exception e) {
            redisTemplate.delete(redisKey);
            throw e;
        }
    }

    private String generateKey(ProceedingJoinPoint point, Idempotent idempotent) {
        Method method = ((org.aspectj.lang.reflect.MethodSignature) point.getSignature()).getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = point.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameters.length; i++) {
            context.setVariable(parameters[i].getName(), args[i]);
        }

        return parser.parseExpression(idempotent.key()).getValue(context, String.class);
    }
}
