package com.club.management.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    String key();
    int expireSeconds() default 10;
    String message() default "请勿重复提交";
}
