package com.club.management.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    String module() default "";
    String operation() default "";
    boolean saveRequestData() default true;
    boolean saveResponseData() default false;
}
