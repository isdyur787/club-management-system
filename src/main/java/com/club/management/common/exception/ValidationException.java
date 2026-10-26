package com.club.management.common.exception;

public class ValidationException extends BusinessException {
    public ValidationException(ErrorCode errorCode) { super(errorCode); }
    public ValidationException(ErrorCode errorCode, String detail) { super(errorCode, detail); }
}
